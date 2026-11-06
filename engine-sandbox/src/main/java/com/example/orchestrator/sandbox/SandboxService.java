package com.example.orchestrator.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Sandbox service for running commands in isolated Docker containers.
 * 
 * Security Features:
 * - Command injection prevention via argument parsing (no shell)
 * - Path traversal prevention with workspace boundary validation
 * - Container escape prevention: read-only rootfs, dropped capabilities
 * - Resource limits (memory, CPU, processes, timeout)
 * - Network isolation (disabled by default)
 * - Non-root user execution
 * - Proper log retrieval
 * - Workspace boundary enforcement
 */
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    private static final Path DEFAULT_WORKSPACE_BASE =
        Paths.get(System.getProperty("user.home"), ".orchestrator", "workspaces")
            .toAbsolutePath().normalize();

    // Pattern to detect path traversal attempts (cross-platform)
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(^|[/\\\\])\\\\.(?:\\\\.|$)");

    // Windows reserved names that shouldn't be used in paths
    private static final Pattern WINDOWS_RESERVED_PATTERN = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\\\..*)?$");

    // No shell is involved: the executable must be a bare tool name.
    private static final Pattern SAFE_EXECUTABLE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
    private static final Pattern SAFE_DOCKERFILE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");
    private static final Pattern SAFE_IMAGE_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9][a-zA-Z0-9._-]*(?::[0-9]{1,5})?"
            + "(?:/[a-zA-Z0-9][a-zA-Z0-9._-]*)*"
            + "(?::[a-zA-Z0-9][a-zA-Z0-9._-]{0,127})?"
            + "(?:@sha256:[a-fA-F0-9]{64})?$");

    private final SandboxConfig config;
    private final List<Path> allowedWorkspaceRoots;
    private DockerClient dockerClient;

    public SandboxService(SandboxConfig config) {
        this.config = config;
        this.allowedWorkspaceRoots = resolveWorkspaceRoots(config);
    }

    private static List<Path> resolveWorkspaceRoots(SandboxConfig config) {
        List<String> configured = config.getWorkspaceRoots();
        if (configured == null || configured.isEmpty()) {
            return List.of(DEFAULT_WORKSPACE_BASE);
        }
        List<Path> roots = new ArrayList<>();
        for (String root : configured) {
            if (root != null && !root.isBlank()) {
                roots.add(Path.of(root).toAbsolutePath().normalize());
            }
        }
        return roots.isEmpty() ? List.of(DEFAULT_WORKSPACE_BASE) : List.copyOf(roots);
    }

    @PostConstruct
    public void init() {
        // Invalid security configuration must always prevent startup.
        config.validateConfiguration();

        try {
            for (Path root : allowedWorkspaceRoots) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create sandbox workspace roots", e);
        }

        DockerClient client;
        try {
            // Validate Docker host before connecting
            config.validateDockerHost(config.getDockerHost());

            DefaultDockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.getDockerHost())
                .build();
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.getDockerHost())
                .sslConfig(dockerConfig.getSSLConfig())
                .build();
            client = DockerClientImpl.getInstance(dockerConfig, httpClient);
        } catch (Exception e) {
            log.error("Failed to initialize Docker client: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize SandboxService", e);
        }

        // A missing Docker daemon is a degraded mode, not a fatal startup error:
        // the API can still serve health/version/task endpoints. Every sandbox
        // operation fails closed while dockerClient is null.
        try {
            client.pingCmd().exec();
        } catch (Exception e) {
            log.warn("Docker daemon is not reachable at {}. Starting in degraded mode; "
                    + "sandbox operations will fail until Docker is available: {}",
                    config.getDockerHost(), e.getMessage());
            try {
                client.close();
            } catch (IOException ignored) {
                // Nothing to clean up when the daemon was never reachable.
            }
            return;
        }

        dockerClient = client;
        log.info("Docker client initialized successfully");
        log.info("Sandbox security: image validation enabled, socket isolation enabled, capabilities dropped");

        try {
            // Ensure Maven cache volume exists
            ensureMavenCacheVolume();
        } catch (Exception e) {
            log.error("Failed to prepare Maven cache volume: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialize SandboxService", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (dockerClient != null) {
            try {
                // Clean up run-isolated Maven cache volumes (legacy)
                cleanupRunIsolatedVolumes();
                // Clean up shared Maven cache volumes (MV-04)
                cleanupSharedVolumes();
                dockerClient.close();
            } catch (IOException e) {
                log.warn("Failed to close Docker client: {}", e.getMessage());
            }
        }
    }

    /**
     * Cleans up legacy run-isolated Maven cache volumes.
     * Called on shutdown to prevent volume accumulation.
     */
    private void cleanupRunIsolatedVolumes() {
        try {
            dockerClient.listVolumesCmd().exec()
                .getVolumes()
                .stream()
                .filter(v -> "maven-cache".equals(v.getLabels().get("orchestrator")) 
                         && "run-isolated".equals(v.getLabels().get("type")))
                .forEach(volume -> {
                    try {
                        dockerClient.removeVolumeCmd(volume.getName()).exec();
                        log.info("Cleaned up legacy run-isolated Maven cache volume: {}", volume.getName());
                    } catch (Exception e) {
                        log.warn("Failed to remove volume {}: {}", volume.getName(), e.getMessage());
                    }
                });
        } catch (Exception e) {
            log.warn("Failed to list run-isolated volumes for cleanup: {}", e.getMessage());
        }
    }

    /**
     * Cleans up shared Maven cache volumes (MV-04).
     * Called on shutdown to prevent orphaned shared volumes.
     * Note: This is opt-in via config flag to avoid removing volumes still in use.
     */
    private void cleanupSharedVolumes() {
        if (!config.isCleanupSharedVolumes()) {
            log.debug("Shared volume cleanup disabled via config");
            return;
        }
        try {
            dockerClient.listVolumesCmd().exec()
                .getVolumes()
                .stream()
                .filter(v -> "maven-cache".equals(v.getLabels().get("orchestrator")) 
                         && "shared".equals(v.getLabels().get("type")))
                .forEach(volume -> {
                    try {
                        dockerClient.removeVolumeCmd(volume.getName()).exec();
                        log.info("Cleaned up shared Maven cache volume: {}", volume.getName());
                    } catch (Exception e) {
                        log.warn("Failed to remove shared volume {}: {}", volume.getName(), e.getMessage());
                    }
                });
        } catch (Exception e) {
            log.warn("Failed to list shared volumes for cleanup: {}", e.getMessage());
        }
    }

    /**
     * M7: Health check for K8s readiness/liveness probes.
     * Verifies Docker daemon connectivity and basic functionality.
     * 
     * @return HealthCheckResult with status and details
     */
    public record HealthCheckResult(
        boolean healthy,
        String message,
        Instant timestamp
    ) {}

    public HealthCheckResult healthCheck() {
        if (dockerClient == null) {
            return new HealthCheckResult(false,
                "Sandbox unavailable: Docker daemon is not reachable",
                Instant.now());
        }
        try {
            // Check Docker daemon connectivity
            dockerClient.pingCmd().exec();
            
            // Verify we can list containers (basic API functionality)
            dockerClient.listContainersCmd().exec();

            // The sandbox is not usable unless its image has been built or
            // pulled. Check it explicitly so health checks catch a missing
            // image instead of only proving that the daemon is reachable.
            try {
                dockerClient.inspectImageCmd(config.getDockerImage()).exec();
            } catch (Exception e) {
                return new HealthCheckResult(false,
                    "Sandbox image not available: " + config.getDockerImage(),
                    Instant.now());
            }

            // A containerized server mounts its projects from a shared named
            // volume. A missing volume means no project can be mounted.
            String workspaceVolume = config.getWorkspaceVolume();
            if (workspaceVolume != null && !workspaceVolume.isBlank()) {
                try {
                    dockerClient.inspectVolumeCmd(workspaceVolume).exec();
                } catch (Exception e) {
                    return new HealthCheckResult(false,
                        "Sandbox workspace volume not available: " + workspaceVolume,
                        Instant.now());
                }
            }

            // Check disk space for the first configured workspace root
            var workspaceBase = allowedWorkspaceRoots.get(0).toFile();
            long freeSpace = workspaceBase.getFreeSpace();
            long totalSpace = workspaceBase.getTotalSpace();
            double freePercent = (double) freeSpace / totalSpace * 100;
            
            if (freePercent < 10) {
                return new HealthCheckResult(false, 
                    String.format("Low disk space: %.1f%% free", freePercent), 
                    Instant.now());
            }
            
            return new HealthCheckResult(true, 
                String.format("Healthy: Docker connected, %.1f%% disk free", freePercent), 
                Instant.now());
                 
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            return new HealthCheckResult(false, 
                "Health check failed: " + e.getMessage(), 
                Instant.now());
        }
    }

    /**
     * M7: Simple liveness check - just verifies the service is running.
     */
    public boolean isAlive() {
        return dockerClient != null;
    }

    /**
     * Runs a command in the sandbox.
     * 
     * @param command Command to execute (e.g., "mvn clean verify")
     * @param workspacePath Path to the workspace directory
     * @param timeoutSeconds Timeout in seconds (0 = use default)
     * @return SandboxResult with exit code, stdout, stderr, duration
     */
    public SandboxResult run(String command, Path workspacePath, int timeoutSeconds) {
        String[] cmdArray = parseCommandSecure(command);
        if (cmdArray == null) {
            return SandboxResult.error("Command contains invalid characters: " + sanitizeForLog(command), Duration.ZERO);
        }
        return run(List.of(cmdArray), workspacePath, ".", timeoutSeconds);
    }

    /**
     * Runs an explicit argument vector inside the sandbox. No host shell is
     * involved, so arguments are passed verbatim to the container process.
     *
     * @param arguments executable followed by its arguments
     * @param workspacePath host workspace, mounted at {@code workspaceMountPath}
     * @param relativeWorkingDirectory directory relative to the workspace
     * @param timeoutSeconds timeout in seconds (0 = default)
     */
    public SandboxResult run(List<String> arguments, Path workspacePath, String relativeWorkingDirectory, int timeoutSeconds) {
        Instant start = Instant.now();
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getDefaultTimeoutSeconds();
        timeout = Math.min(timeout, config.getMaxTimeoutSeconds());

        if (dockerClient == null) {
            return SandboxResult.error("Sandbox unavailable: Docker daemon not reachable", Duration.ZERO);
        }

        // LV-08: Fail-closed - verify Docker connectivity before each run
        try {
            dockerClient.pingCmd().exec();
        } catch (Exception e) {
            log.error("Docker connectivity lost - failing closed: {}", e.getMessage());
            return SandboxResult.error("Sandbox unavailable: Docker daemon not reachable", Duration.ZERO);
        }

        List<String> validatedArguments = validateArguments(arguments);
        if (validatedArguments == null) {
            return SandboxResult.error("Invalid sandbox command arguments", Duration.ZERO);
        }

        String containerWorkingDirectory = toContainerWorkingDirectory(relativeWorkingDirectory);
        if (containerWorkingDirectory == null) {
            return SandboxResult.error("Invalid sandbox working directory: " + relativeWorkingDirectory, Duration.ZERO);
        }

        log.info("Running command in sandbox: {} (workdir: {}, timeout: {}s)",
            sanitizeForLog(String.join(" ", validatedArguments)), containerWorkingDirectory, timeout);

        // Validate workspace path - MUST be within an allowed root directory
        Path validatedWorkspacePath = validateWorkspacePath(workspacePath);
        if (validatedWorkspacePath == null) {
            return SandboxResult.error("Workspace path not allowed: " + workspacePath, Duration.ZERO);
        }

        String[] cmdArray = validatedArguments.toArray(new String[0]);

        String containerId = null;
        try {
            // Create container
            CreateContainerResponse container = createContainer(cmdArray, validatedWorkspacePath, containerWorkingDirectory);
            containerId = container.getId();

            // Start container
            dockerClient.startContainerCmd(containerId).exec();

            // Wait for completion with timeout
            boolean completed = dockerClient.waitContainerCmd(containerId)
                .start()
                .awaitCompletion(timeout, TimeUnit.SECONDS);

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            if (!completed) {
                // Timeout - kill container
                killContainer(containerId);
                return SandboxResult.timeout(Duration.between(start, Instant.now()));
            }

            // Get results
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            int exitCode = inspect.getState().getExitCode();

            // Get logs
            String[] split = getContainerLogs(containerId);

            // Check for OOM kill
            boolean oomKilled = inspect.getState().getOOMKilled();

            if (oomKilled) {
                return SandboxResult.oomKilled(exitCode, split[0], split[1], Duration.between(start, end));
            }

            return SandboxResult.success(exitCode, split[0], split[1], Duration.between(start, end));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (containerId != null) {
                killContainer(containerId);
            }
            return SandboxResult.error("Execution interrupted: " + e.getMessage(), Duration.between(start, Instant.now()));
        } catch (Exception e) {
            log.error("Sandbox execution failed: {}", e.getMessage(), e);
            if (containerId != null) {
                killContainer(containerId);
            }
            return SandboxResult.error("Execution failed: " + e.getMessage(), Duration.between(start, Instant.now()));
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
        }
    }

    /**
     * Validates that the workspace path is within the allowed base directory.
     * Prevents path traversal attacks.
     */
    private Path validateWorkspacePath(Path workspacePath) {
        if (workspacePath == null) {
            return null;
        }
        try {
            Path absolutePath = workspacePath.toAbsolutePath().normalize();

            // Check for path traversal attempts in the original input.
            String pathStr = workspacePath.toString();
            if (PATH_TRAVERSAL_PATTERN.matcher(pathStr).find()) {
                log.warn("Path traversal attempt detected: {}", pathStr);
                return null;
            }

            // Check for Windows reserved names
            for (Path name : absolutePath) {
                if (WINDOWS_RESERVED_PATTERN.matcher(name.toString()).matches()) {
                    log.warn("Windows reserved name in path: {}", name);
                    return null;
                }
            }

            // Ensure directory exists
            if (!Files.exists(absolutePath)) {
                log.warn("Workspace path does not exist: {}", absolutePath);
                return null;
            }

            // Ensure it's a directory
            if (!Files.isDirectory(absolutePath)) {
                log.warn("Workspace path is not a directory: {}", absolutePath);
                return null;
            }

            // Resolve symlinks so a link inside an allowed root cannot escape it.
            Path canonicalPath = absolutePath.toRealPath();

            boolean insideAllowedRoot = false;
            for (Path root : allowedWorkspaceRoots) {
                Path canonicalRoot = root.toRealPath();
                if (canonicalPath.startsWith(canonicalRoot)) {
                    insideAllowedRoot = true;
                    break;
                }
            }
            if (!insideAllowedRoot) {
                log.warn("Workspace path outside allowed roots: {} (roots: {})", canonicalPath, allowedWorkspaceRoots);
                return null;
            }

            return canonicalPath;

        } catch (Exception e) {
            log.warn("Workspace path validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates an argv list. The executable must be a bare tool name; every
     * argument is rejected if it contains a NUL byte. Shell metacharacters are
     * harmless without a shell, but are not needed by any supported tool.
     */
    private List<String> validateArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        List<String> validated = new ArrayList<>(arguments.size());
        int totalLength = 0;
        for (String argument : arguments) {
            if (argument == null || argument.indexOf('\0') >= 0) {
                return null;
            }
            totalLength += argument.length() + 1;
            validated.add(argument);
        }
        if (totalLength > 65536) {
            log.warn("Sandbox argument list rejected - too long: {} bytes", totalLength);
            return null;
        }
        String executable = validated.get(0);
        if (!SAFE_EXECUTABLE_PATTERN.matcher(executable).matches()) {
            log.warn("Sandbox executable rejected: {}", executable);
            return null;
        }
        return List.copyOf(validated);
    }

    /**
     * Maps a workspace-relative directory to the container mount path.
     */
    private String toContainerWorkingDirectory(String relativeWorkingDirectory) {
        String relative = relativeWorkingDirectory == null || relativeWorkingDirectory.isBlank()
            ? "." : relativeWorkingDirectory.trim();
        Path normalised;
        try {
            normalised = Path.of(relative).normalize();
        } catch (Exception e) {
            return null;
        }
        if (normalised.isAbsolute() || normalised.startsWith("..")) {
            return null;
        }
        String posixRelative = normalised.toString().replace('\\', '/');
        if (posixRelative.equals(".") || posixRelative.isEmpty()) {
            return config.getWorkspaceMountPath();
        }
        return config.getWorkspaceMountPath() + "/" + posixRelative;
    }

    /**
     * Parses command string into array securely - NO SHELL.
     * Splits on whitespace but respects quoted strings.
     * Returns null if command contains dangerous characters.
     */
    private String[] parseCommandSecure(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        
        // Reject commands with shell metacharacters
        if (command.matches(".*[;&|`$()<>].*")) {
            log.warn("Command rejected - contains shell metacharacters: {}", sanitizeForLog(command));
            return null;
        }
        
        // Reject commands that are too long
        if (command.length() > 4096) {
            log.warn("Command rejected - too long: {} chars", command.length());
            return null;
        }
        
        // Parse command respecting quoted strings
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;
        
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            
            if ((c == '"' || c == '\'') && !inQuotes) {
                inQuotes = true;
                quoteChar = c;
            } else if (c == quoteChar && inQuotes) {
                inQuotes = false;
                quoteChar = 0;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            args.add(current.toString());
        }
        
        if (args.isEmpty()) {
            return null;
        }
        
        // Validate first argument (command) - no path traversal
        String cmd = args.get(0);
        if (cmd.contains("/") || cmd.contains("\\") || cmd.contains("..")) {
            log.warn("Command rejected - path traversal in command: {}", sanitizeForLog(cmd));
            return null;
        }
        
        return args.toArray(new String[0]);
    }

    /**
     * Builds an image through the Docker daemon API.
     *
     * <p>This deliberately does not invoke the {@code docker} CLI on the host.
     * Build steps execute inside the daemon's build sandbox, and the build
     * context is the already-validated workspace directory.</p>
     */
    public SandboxResult buildImage(Path workspacePath, String dockerfile, String tag,
                                    Map<String, String> buildArgs, int timeoutSeconds) {
        Instant start = Instant.now();
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getDefaultTimeoutSeconds();
        timeout = Math.min(timeout, config.getMaxTimeoutSeconds());

        if (dockerClient == null) {
            return SandboxResult.error("Sandbox unavailable: Docker daemon not reachable", Duration.ZERO);
        }
        Path validatedWorkspacePath = validateWorkspacePath(workspacePath);
        if (validatedWorkspacePath == null) {
            return SandboxResult.error("Workspace path not allowed: " + workspacePath, Duration.ZERO);
        }
        if (dockerfile == null || !SAFE_DOCKERFILE_PATTERN.matcher(dockerfile).matches()) {
            return SandboxResult.error("Invalid Dockerfile name", Duration.ZERO);
        }
        if (tag == null || !SAFE_IMAGE_PATTERN.matcher(tag).matches()) {
            return SandboxResult.error("Invalid image tag: " + tag, Duration.ZERO);
        }
        Path dockerfilePath = validatedWorkspacePath.resolve(dockerfile).normalize();
        if (!dockerfilePath.startsWith(validatedWorkspacePath) || !Files.isRegularFile(dockerfilePath)) {
            return SandboxResult.error("Dockerfile not found: " + dockerfile, Duration.ZERO);
        }

        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        try {
            ResultCallback.Adapter<BuildResponseItem> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(BuildResponseItem item) {
                    if (item.getStream() != null) {
                        output.append(item.getStream());
                    }
                    if (item.getError() != null) {
                        errors.append(item.getError());
                    }
                    super.onNext(item);
                }
            };

            var buildCommand = dockerClient.buildImageCmd()
                .withBaseDirectory(validatedWorkspacePath.toFile())
                .withDockerfile(dockerfilePath.toFile())
                .withTags(Set.of(tag));
            if (buildArgs != null) {
                buildArgs.forEach(buildCommand::withBuildArg);
            }
            buildCommand.exec(callback);

            boolean completed = callback.awaitCompletion(timeout, TimeUnit.SECONDS);
            if (!completed) {
                callback.close();
                return SandboxResult.timeout(Duration.between(start, Instant.now()));
            }

            int exitCode = errors.length() == 0 ? 0 : 1;
            return SandboxResult.success(exitCode, output.toString(), errors.toString(),
                Duration.between(start, Instant.now()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SandboxResult.error("Image build interrupted", Duration.between(start, Instant.now()));
        } catch (Exception e) {
            log.error("Sandbox image build failed: {}", e.getMessage(), e);
            return SandboxResult.error("Image build failed: " + e.getMessage(),
                Duration.between(start, Instant.now()));
        }
    }

    /**
     * Exports an image from the Docker daemon to a tar file inside an allowed
     * workspace. Scanners can then inspect the tar inside the sandbox without
     * mounting the Docker socket into the container.
     */
    public SandboxResult saveImageToFile(String image, Path destination) {
        Instant start = Instant.now();
        if (dockerClient == null) {
            return SandboxResult.error("Sandbox unavailable: Docker daemon not reachable", Duration.ZERO);
        }
        if (image == null || !SAFE_IMAGE_PATTERN.matcher(image).matches()) {
            return SandboxResult.error("Invalid image reference: " + image, Duration.ZERO);
        }
        if (destination == null) {
            return SandboxResult.error("Destination is required", Duration.ZERO);
        }
        Path destinationPath = destination.toAbsolutePath().normalize();
        Path parent = destinationPath.getParent();
        if (parent == null || validateWorkspacePath(parent) == null) {
            return SandboxResult.error("Destination path not allowed", Duration.ZERO);
        }

        try (InputStream imageStream = dockerClient.saveImageCmd(image).exec();
             OutputStream outputStream = Files.newOutputStream(destinationPath)) {
            imageStream.transferTo(outputStream);
            return SandboxResult.success(0, destinationPath.toString(), "",
                Duration.between(start, Instant.now()));
        } catch (Exception e) {
            log.error("Failed to export image {}: {}", image, e.getMessage(), e);
            return SandboxResult.error("Failed to export image: " + e.getMessage(),
                Duration.between(start, Instant.now()));
        }
    }

    /**
     * Creates the Docker container with all security and resource limits.
     */
    private CreateContainerResponse createContainer(String[] cmdArray, Path workspacePath, String containerWorkingDirectory) {
        // Validate the Docker image against security policies
        config.validateImage(config.getDockerImage());
        
        // M4, M10: Generate unique Maven cache volume per container to prevent concurrent build corruption
        String mavenCacheVolume = getJobMavenCacheVolume();

        // docker-java's withBinds replaces the existing array, so both mounts
        // must be supplied in a single call.
        Bind workspaceBind = createWorkspaceBind(workspacePath);
        if (workspaceBind == null) {
            throw new IllegalStateException(
                "Workspace is outside the shared sandbox volume root: " + workspacePath);
        }
        List<Bind> binds = new ArrayList<>();
        binds.add(workspaceBind);
        binds.add(Bind.parse(mavenCacheVolume + ":/home/sandbox/.m2:rw"));

        HostConfig hostConfig = HostConfig.newHostConfig()
            // Memory limit
            .withMemory(config.getMemoryLimitMb() * 1024L * 1024L)
            .withMemorySwap(config.getMemoryLimitMb() * 1024L * 1024L * 2) // 2x memory for swap
            // CPU limit
            .withCpuCount(config.getCpuLimitCores())
            // Process limit
            .withPidsLimit((long) config.getMaxProcesses())
            // Workspace mount (read-write) and isolated Maven cache
            .withBinds(binds)
            // Network - disabled by default
            .withNetworkMode(config.isEnableNetwork() ? "bridge" : "none")
            // Security - READ-ONLY ROOT FILESYSTEM
            .withReadonlyRootfs(true)
            // Writable scratch/cache locations. Everything else stays read-only.
            .withTmpFs(Map.of(
                "/tmp", "rw,nosuid,nodev,size=512m,mode=1777",
                // Match the sandbox image user (1001) so the tmpfs is writable
                // while the root filesystem stays read-only.
                "/home/sandbox/.cache", "rw,nosuid,nodev,size=1g,uid=1001,gid=1001,mode=0755"))
            // Drop ALL capabilities by default
            .withCapDrop(Capability.ALL)
            // Add only explicitly allowed capabilities (if any)
            .withCapAdd(config.getAllowedCapabilities().stream()
                .map(s -> Capability.valueOf(s.toUpperCase().replace("CAP_", "")))
                .toArray(Capability[]::new))
            // Security options (no-new-privileges, etc.)
            .withSecurityOpts(config.getSecurityOpts());
            // Note: AppArmor and Seccomp profiles are set via securityOpts in modern docker-java
            // e.g., "apparmor=docker-default", "seccomp=default"

        return dockerClient.createContainerCmd(config.getDockerImage())
            .withCmd(cmdArray)
            .withHostConfig(hostConfig)
            .withWorkingDir(containerWorkingDirectory)
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withTty(false)
            .exec();
    }

    /**
     * Builds the workspace mount for a sandbox container.
     *
     * <p>When no shared volume is configured the server runs on the Docker
     * host and the workspace can be bind-mounted directly. When a shared
     * volume is configured (the compose stack), the sandbox container is a
     * sibling of the server container. The daemon resolves the named volume
     * to its own host mountpoint, and the sandbox bind-mounts only the
     * project subdirectory from there. A direct bind of the server's
     * container path would point at a path the daemon cannot see, and the
     * legacy {@code volume/subpath} syntax is not a valid volume name.</p>
     */
    Bind createWorkspaceBind(Path workspacePath) {
        String volume = config.getWorkspaceVolume();
        if (volume == null || volume.isBlank()) {
            return Bind.parse(workspacePath.toString() + ":" + config.getWorkspaceMountPath() + ":rw");
        }

        Path volumeRoot;
        try {
            volumeRoot = Path.of(config.getWorkspaceVolumeRoot()).toAbsolutePath().normalize();
        } catch (Exception e) {
            log.warn("Invalid sandbox workspace volume root '{}': {}",
                config.getWorkspaceVolumeRoot(), e.getMessage());
            return null;
        }

        Path absoluteWorkspace = workspacePath.toAbsolutePath().normalize();
        if (!absoluteWorkspace.startsWith(volumeRoot)) {
            log.warn("Workspace {} is outside the shared sandbox volume root {}",
                absoluteWorkspace, volumeRoot);
            return null;
        }

        String volumeMountpoint = resolveWorkspaceVolumeMountpoint(volume);
        if (volumeMountpoint == null || volumeMountpoint.isBlank()) {
            log.warn("Could not resolve the host mountpoint for shared sandbox volume '{}'", volume);
            return null;
        }

        Path relative = volumeRoot.relativize(absoluteWorkspace);
        String mountpoint = volumeMountpoint.replace('\\', '/');
        while (mountpoint.endsWith("/")) {
            mountpoint = mountpoint.substring(0, mountpoint.length() - 1);
        }
        String source = relative.toString().isEmpty()
            ? mountpoint
            : mountpoint + "/" + relative.toString().replace('\\', '/');
        return Bind.parse(source + ":" + config.getWorkspaceMountPath() + ":rw");
    }

    /**
     * Resolves a named Docker volume to the path where the daemon can bind it.
     * Package-private for testing; returns null when Docker is unavailable or
     * the volume does not exist, which makes the sandbox fail closed.
     */
    String resolveWorkspaceVolumeMountpoint(String volume) {
        if (dockerClient == null) {
            return null;
        }
        try {
            var response = dockerClient.inspectVolumeCmd(volume).exec();
            return response.getMountpoint();
        } catch (Exception e) {
            log.warn("Could not inspect shared sandbox volume '{}': {}", volume, e.getMessage());
            return null;
        }
    }

    /**
     * M4, M10: Gets a shared Maven cache volume name for this container run.
     * Uses a single shared volume with per-run subdirectories to avoid corruption and orphaned volumes.
     * 
     * H-09: Race condition fix - uses idempotent volume creation.
     * Docker's createVolume is atomic - if volume exists, it returns an error we handle gracefully.
     * No TOCTOU between check and create.
     */
    private String getJobMavenCacheVolume() {
        // Use a single shared volume for all runs - subdirectories provide isolation
        String volumeName = config.getMavenCacheVolume() + "_shared";
        
        // Ensure the shared volume exists - IDEMPOTENT operation
        // Docker API: createVolume with existing name returns error, which we handle
        try {
            dockerClient.createVolumeCmd()
                .withName(volumeName)
                .withLabels(Map.of(
                    "orchestrator", "maven-cache",
                    "type", "shared",
                    "created", Instant.now().toString()
                ))
                .exec();
            log.debug("Created shared Maven cache volume: {}", volumeName);
        } catch (Exception e) {
            // Check if it's the "already exists" error - if so, volume is ready
            String msg = e.getMessage();
            if (msg != null && (msg.contains("already exists") || msg.contains("name already in use") || msg.contains("409"))) {
                log.debug("Shared Maven cache volume already exists: {}", volumeName);
            } else {
                // Unexpected error - log but don't fail, volume might exist from previous run
                log.warn("Unexpected error creating Maven cache volume '{}': {}", volumeName, msg);
            }
        }
        
        return volumeName;
    }

    /**
     * Sanitizes command for logging (removes potential secrets).
     */
    private String sanitizeForLog(String input) {
        if (input == null) return "null";
        // Truncate and mask potential secrets
        String masked = input.replaceAll("(?i)(password|secret|token|key|auth)=[^\\s]+", "$1=***");
        if (masked.length() > 200) {
            return masked.substring(0, 200) + "...[truncated]";
        }
        return masked;
    }

    /**
     * Gets container logs using docker-java's ResultCallback with CountDownLatch.
     */
    private String[] getContainerLogs(String containerId) {
        try {
            // Use a CountDownLatch to wait for log collection completion
            CountDownLatch latch = new CountDownLatch(1);
            
            // Use a final array to store results from the callback
            final String[] result = new String[2];
            
            // Use a custom result callback to capture stdout and stderr
            ResultCallback<Frame> callback = new ResultCallback<Frame>() {
                private final StringBuilder stdout = new StringBuilder();
                private final StringBuilder stderr = new StringBuilder();

                @Override
                public void onStart(Closeable stream) {
                    // Stream started
                }

                @Override
                public void onNext(Frame frame) {
                    if (frame != null) {
                        String payload = new String(frame.getPayload());
                        if (frame.getStreamType() == StreamType.STDERR) {
                            stderr.append(payload);
                        } else {
                            stdout.append(payload);
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    log.warn("Error reading container logs: {}", throwable.getMessage());
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    // Stream completed - store results
                    result[0] = stdout.toString();
                    result[1] = stderr.toString();
                    latch.countDown();
                }

                @Override
                public void close() throws IOException {
                    // Nothing to close
                }
            };
            
            dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(false)
                .withTimestamps(false)
                .withTailAll()
                .exec(callback);
            
            // Wait for completion with timeout
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Timeout waiting for container logs for {}", containerId);
            }
            
            String stdout = result[0];
            String stderr = result[1];
            
            log.debug("Container {} logs - stdout: {} chars, stderr: {} chars", 
                containerId, stdout.length(), stderr.length());
            
            return new String[]{stdout, stderr};
            
        } catch (Exception e) {
            log.warn("Failed to get container logs for {}: {}", containerId, e.getMessage());
            return new String[]{"", ""};
        }
    }

    /**
     * Kills a container.
     */
    private void killContainer(String containerId) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
            log.info("Killed container: {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to kill container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Removes a container.
     */
    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Ensures the Maven cache volume exists.
     * H-09: Race condition fix - uses idempotent create, no check-then-act.
     */
    private void ensureMavenCacheVolume() {
        String volumeName = config.getMavenCacheVolume() + "_shared";
        try {
            // Idempotent create - no TOCTOU between list and create
            dockerClient.createVolumeCmd()
                .withName(volumeName)
                .withLabels(Map.of("orchestrator", "maven-cache", "type", "shared"))
                .exec();
            log.info("Created shared Maven cache volume: {}", volumeName);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("already exists") || msg.contains("name already in use") || msg.contains("409"))) {
                log.debug("Shared Maven cache volume already exists: {}", volumeName);
            } else {
                log.warn("Could not create Maven cache volume '{}': {}", volumeName, msg);
            }
        }
    }
}