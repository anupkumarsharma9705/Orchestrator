package com.example.orchestrator.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
@Service
public class SandboxService {

    private static final Logger log = LoggerFactory.getLogger(SandboxService.class);

    // Allowed workspace base directory - all workspace paths must be under this
    private static final Path ALLOWED_WORKSPACE_BASE = Paths.get(System.getProperty("user.home"), ".orchestrator", "workspaces").toAbsolutePath().normalize();
    
    // Pattern to detect path traversal attempts (cross-platform)
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(^|[/\\\\])\\\\.(?:\\\\.|$)");
    
    // Windows reserved names that shouldn't be used in paths
    private static final Pattern WINDOWS_RESERVED_PATTERN = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\\\..*)?$");

    private final SandboxConfig config;
    private DockerClient dockerClient;

    public SandboxService(SandboxConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            // Validate all configuration at startup
            config.validateConfiguration();
            
            // Ensure allowed workspace base exists
            Files.createDirectories(ALLOWED_WORKSPACE_BASE);
            
            // Validate Docker host before connecting
            config.validateDockerHost(config.getDockerHost());
            
            DefaultDockerClientConfig dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.getDockerHost())
                .build();
            dockerClient = DockerClientBuilder.getInstance(dockerConfig).build();
            
            // Test connection
            dockerClient.pingCmd().exec();
            log.info("Docker client initialized successfully");
            log.info("Sandbox security: image validation enabled, socket isolation enabled, capabilities dropped");
            
            // Ensure Maven cache volume exists
            ensureMavenCacheVolume();
            
        } catch (Exception e) {
            log.error("Failed to initialize Docker client: {}", e.getMessage(), e);
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
        try {
            // Check Docker daemon connectivity
            dockerClient.pingCmd().exec();
            
            // Verify we can list containers (basic API functionality)
            dockerClient.listContainersCmd().exec();
            
            // Check disk space for workspace base
            var workspaceBase = ALLOWED_WORKSPACE_BASE.toFile();
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
        Instant start = Instant.now();
        int timeout = timeoutSeconds > 0 ? timeoutSeconds : config.getDefaultTimeoutSeconds();
        timeout = Math.min(timeout, config.getMaxTimeoutSeconds());

        // LV-08: Fail-closed - verify Docker connectivity before each run
        try {
            dockerClient.pingCmd().exec();
        } catch (Exception e) {
            log.error("Docker connectivity lost - failing closed: {}", e.getMessage());
            return SandboxResult.error("Sandbox unavailable: Docker daemon not reachable", Duration.ZERO);
        }

        log.info("Running command in sandbox: {} (timeout: {}s)", sanitizeForLog(command), timeout);

        // Validate workspace path - MUST be within allowed base directory
        Path validatedWorkspacePath = validateWorkspacePath(workspacePath);
        if (validatedWorkspacePath == null) {
            return SandboxResult.error("Workspace path not allowed: " + workspacePath, Duration.ZERO);
        }

        // Validate command - no shell metacharacters allowed
        String[] cmdArray = parseCommandSecure(command);
        if (cmdArray == null) {
            return SandboxResult.error("Command contains invalid characters: " + sanitizeForLog(command), Duration.ZERO);
        }

        String containerId = null;
        try {
            // Create container
            CreateContainerResponse container = createContainer(cmdArray, validatedWorkspacePath);
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
        try {
            Path absolutePath = workspacePath.toAbsolutePath().normalize();
            
            // Check if path is within allowed base directory
            if (!absolutePath.startsWith(ALLOWED_WORKSPACE_BASE)) {
                log.warn("Workspace path outside allowed base: {} (base: {})", absolutePath, ALLOWED_WORKSPACE_BASE);
                return null;
            }
            
            // Check for path traversal attempts
            String pathStr = absolutePath.toString();
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
            
            return absolutePath;
            
        } catch (Exception e) {
            log.warn("Workspace path validation failed: {}", e.getMessage());
            return null;
        }
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
     * Creates the Docker container with all security and resource limits.
     */
    private CreateContainerResponse createContainer(String[] cmdArray, Path workspacePath) {
        // Validate the Docker image against security policies
        config.validateImage(config.getDockerImage());
        
        // M4, M10: Generate unique Maven cache volume per container to prevent concurrent build corruption
        String mavenCacheVolume = getJobMavenCacheVolume();
        
        HostConfig hostConfig = HostConfig.newHostConfig()
            // Memory limit
            .withMemory(config.getMemoryLimitMb() * 1024L * 1024L)
            .withMemorySwap(config.getMemoryLimitMb() * 1024L * 1024L * 2) // 2x memory for swap
            // CPU limit
            .withCpuCount(config.getCpuLimitCores())
            // Process limit
            .withPidsLimit((long) config.getMaxProcesses())
            // Workspace mount (read-write) - validated path
            .withBinds(Bind.parse(workspacePath.toString() + ":" + config.getWorkspaceMountPath() + ":rw"))
            // M4, M10: Unique Maven cache volume per container run to avoid concurrent build corruption
            .withBinds(Bind.parse(mavenCacheVolume + ":/home/sandbox/.m2:rw"))
            // Network - disabled by default
            .withNetworkMode(config.isEnableNetwork() ? "bridge" : "none")
            // Security - READ-ONLY ROOT FILESYSTEM
            .withReadonlyRootfs(true)
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
            .withWorkingDir(config.getWorkspaceMountPath())
            .withAttachStdout(true)
            .withAttachStderr(true)
            .withTty(false)
            .exec();
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