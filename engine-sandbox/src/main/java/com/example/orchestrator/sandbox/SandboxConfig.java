package com.example.orchestrator.sandbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration properties for the Sandbox.
 * 
 * Security features:
 * - Image validation: allowed registries, required tags, deny lists
 * - Socket isolation: validates Docker socket path, supports npipe/tcp
 * - Resource limits with validation
 * - Network isolation by default
 */
@ConfigurationProperties(prefix = "orchestrator.sandbox")
@Validated
public class SandboxConfig {

    // ===== Docker Image Configuration =====
    
    @NotBlank
    private String dockerImage = "orchestrator/sandbox:1.0.0";

    /** 
     * Allowed Docker registries. Empty = allow all (NOT recommended for production).
     * Example: ["docker.io", "ghcr.io", "gcr.io", "registry.internal"]
     */
    private List<String> allowedRegistries = List.of();

    /** 
     * Denied Docker registries. Takes precedence over allowedRegistries.
     * Example: ["malicious.registry.com", "untrusted.io"]
     */
    private List<String> deniedRegistries = List.of();

    /** 
     * Require specific image tag (not "latest"). 
     * When true, "latest" tag is rejected unless explicitly allowed.
     */
    private boolean requireExplicitTag = true;

    /** 
     * Allowed image tags. Empty = allow any tag (if requireExplicitTag=false).
     * Example: ["1.0.0", "1.0.1", "stable"]
     */
    private List<String> allowedTags = List.of();

    /** 
     * Denied image patterns (glob-style). 
     * Example: ["*:latest", "*:dev-*", "malicious/*"]
     */
    private List<String> deniedImagePatterns = List.of("*:latest");

    // ===== Docker Socket/Host Configuration =====
    
    @NotBlank
    private String dockerHost = defaultDockerHost();

    private static String defaultDockerHost() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win")
                ? "npipe:////./pipe/dockerDesktopLinuxEngine"
                : "unix:///var/run/docker.sock";
    }

    /**
     * Allowed Docker socket paths. Prevents socket hijacking.
     *
     * <p>The list must contain the platform default returned by
     * {@link #defaultDockerHost()}, otherwise startup fails validation on that
     * platform. Unix and named-pipe entries are inert when not present.</p>
     *
     * Unix: {@code unix:///var/run/docker.sock}, {@code unix:///run/docker.sock}<br>
     * Windows: {@code npipe:////./pipe/docker_engine},
     * {@code npipe:////./pipe/dockerDesktopLinuxEngine}<br>
     * TCP: {@code tcp://localhost:2376} (requires TLS)
     */
    private List<String> allowedDockerHosts = List.of(
        "tcp://localhost:2376",
        "npipe:////./pipe/docker_engine",
        "npipe:////./pipe/dockerDesktopLinuxEngine",
        "unix:///var/run/docker.sock",
        "unix:///run/docker.sock"
    );

    /** Require TLS for TCP Docker connections */
    private boolean requireTlsForTcp = true;

    /** Path to client certificate for TLS authentication */
    private String clientCertPath = "";

    /** Path to client private key for TLS authentication */
    private String clientKeyPath = "";

    /** Path to CA certificate for verifying Docker daemon */
    private String caCertPath = "";

    // ===== Resource Limits =====
    
    @Positive
    private int defaultTimeoutSeconds = 300;
    
    @Positive
    private int maxTimeoutSeconds = 3600;
    
    @Positive
    private long memoryLimitMb = 2048;
    
    @Positive
    private long cpuLimitCores = 2;
    
    @Positive
    private int maxProcesses = 100;

    // ===== Volume & Workspace Configuration =====
    
    @NotBlank
    private String mavenCacheVolume = "orchestrator-maven-cache";
    
    @NotBlank
    private String workspaceMountPath = "/workspace";

    /**
     * Named Docker volume that holds the workspace tree when the server itself
     * runs in a container (Docker-outside-of-Docker).
     *
     * <p>A bind mount taken from the server container path cannot be resolved
     * by a sibling sandbox container: the daemon sees host paths, not the
     * server's mount namespace. When this is set, workspaces below
     * {@link #workspaceVolumeRoot} are mounted from the named volume instead.
     * Leave empty for a server running directly on the Docker host.</p>
     */
    private String workspaceVolume = "";

    /**
     * Container path where the shared workspace volume is mounted. Must be an
     * absolute path and must match the server's workspace allow-list.
     */
    @NotBlank
    private String workspaceVolumeRoot = "/workspaces";

    /**
     * Host directories that may be mounted into the sandbox.
     *
     * <p>Every project workspace must live below one of these roots. Leaving
     * this empty falls back to {@code ~/.orchestrator/workspaces} so the
     * sandbox never silently accepts an arbitrary host directory.</p>
     */
    private List<String> workspaceRoots = List.of();
    
    /** Enable network access for containers (DANGEROUS - default false) */
    private boolean enableNetwork = false;

    /** Clean up shared Maven cache volumes on shutdown (default false - opt-in) */
    private boolean cleanupSharedVolumes = false;

    // ===== Security Policies =====
    
    /** 
     * Allowed capabilities (empty = drop ALL). 
     * Only add if absolutely necessary.
     * Example: ["CAP_DAC_OVERRIDE"] for specific file operations.
     */
    private List<String> allowedCapabilities = List.of();

    /** 
     * Denied capabilities (additional to default drop ALL).
     * Defense in depth.
     */
    private List<String> deniedCapabilities = List.of(
        "CAP_SYS_ADMIN", "CAP_SYS_RESOURCE", "CAP_SYS_MODULE",
        "CAP_SYS_RAWIO", "CAP_SYS_PACCT", "CAP_SYS_NICE",
        "CAP_SYSLOG", "CAP_DAC_READ_SEARCH", "CAP_LINUX_IMMUTABLE",
        "CAP_MAC_ADMIN", "CAP_MAC_OVERRIDE", "CAP_BPF",
        "CAP_PERFMON", "CAP_CHECKPOINT_RESTORE"
    );

    /** 
     * Security options for container.
     * Example: ["no-new-privileges:true", "seccomp=unconfined"]
     */
    private List<String> securityOpts = List.of("no-new-privileges:true");

    /** 
     * AppArmor profile for container.
     * Example: "docker-default", "unconfined"
     */
    private String apparmorProfile = "docker-default";

    /** 
     * Seccomp profile path.
     * Example: "/etc/docker/seccomp-default.json"
     */
    private String seccompProfile = "default";

    // ===== Getters & Setters =====

    public String getDockerImage() { return dockerImage; }
    public void setDockerImage(String dockerImage) { this.dockerImage = dockerImage; }

    public List<String> getAllowedRegistries() { return allowedRegistries; }
    public void setAllowedRegistries(List<String> allowedRegistries) { this.allowedRegistries = allowedRegistries; }

    public List<String> getDeniedRegistries() { return deniedRegistries; }
    public void setDeniedRegistries(List<String> deniedRegistries) { this.deniedRegistries = deniedRegistries; }

    public boolean isRequireExplicitTag() { return requireExplicitTag; }
    public void setRequireExplicitTag(boolean requireExplicitTag) { this.requireExplicitTag = requireExplicitTag; }

    public List<String> getAllowedTags() { return allowedTags; }
    public void setAllowedTags(List<String> allowedTags) { this.allowedTags = allowedTags; }

    public List<String> getDeniedImagePatterns() { return deniedImagePatterns; }
    public void setDeniedImagePatterns(List<String> deniedImagePatterns) { this.deniedImagePatterns = deniedImagePatterns; }

    public String getDockerHost() { return dockerHost; }
    public void setDockerHost(String dockerHost) { this.dockerHost = dockerHost; }

    public List<String> getAllowedDockerHosts() { return allowedDockerHosts; }
    public void setAllowedDockerHosts(List<String> allowedDockerHosts) { this.allowedDockerHosts = allowedDockerHosts; }

    public boolean isRequireTlsForTcp() { return requireTlsForTcp; }
    public void setRequireTlsForTcp(boolean requireTlsForTcp) { this.requireTlsForTcp = requireTlsForTcp; }

    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }

    public int getMaxTimeoutSeconds() { return maxTimeoutSeconds; }
    public void setMaxTimeoutSeconds(int maxTimeoutSeconds) { this.maxTimeoutSeconds = maxTimeoutSeconds; }

    public long getMemoryLimitMb() { return memoryLimitMb; }
    public void setMemoryLimitMb(long memoryLimitMb) { this.memoryLimitMb = memoryLimitMb; }

    public long getCpuLimitCores() { return cpuLimitCores; }
    public void setCpuLimitCores(long cpuLimitCores) { this.cpuLimitCores = cpuLimitCores; }

    public int getMaxProcesses() { return maxProcesses; }
    public void setMaxProcesses(int maxProcesses) { this.maxProcesses = maxProcesses; }

    public String getMavenCacheVolume() { return mavenCacheVolume; }
    public void setMavenCacheVolume(String mavenCacheVolume) { this.mavenCacheVolume = mavenCacheVolume; }

    public String getWorkspaceMountPath() { return workspaceMountPath; }
    public void setWorkspaceMountPath(String workspaceMountPath) { this.workspaceMountPath = workspaceMountPath; }

    public String getWorkspaceVolume() { return workspaceVolume; }
    public void setWorkspaceVolume(String workspaceVolume) { this.workspaceVolume = workspaceVolume; }

    public String getWorkspaceVolumeRoot() { return workspaceVolumeRoot; }
    public void setWorkspaceVolumeRoot(String workspaceVolumeRoot) { this.workspaceVolumeRoot = workspaceVolumeRoot; }

    public List<String> getWorkspaceRoots() { return workspaceRoots; }
    public void setWorkspaceRoots(List<String> workspaceRoots) { this.workspaceRoots = workspaceRoots; }

    public boolean isEnableNetwork() { return enableNetwork; }
    public void setEnableNetwork(boolean enableNetwork) { this.enableNetwork = enableNetwork; }

    public boolean isCleanupSharedVolumes() { return cleanupSharedVolumes; }
    public void setCleanupSharedVolumes(boolean cleanupSharedVolumes) { this.cleanupSharedVolumes = cleanupSharedVolumes; }

    public List<String> getAllowedCapabilities() { return allowedCapabilities; }
    public void setAllowedCapabilities(List<String> allowedCapabilities) { this.allowedCapabilities = allowedCapabilities; }

    public List<String> getDeniedCapabilities() { return deniedCapabilities; }
    public void setDeniedCapabilities(List<String> deniedCapabilities) { this.deniedCapabilities = deniedCapabilities; }

    public List<String> getSecurityOpts() { return securityOpts; }
    public void setSecurityOpts(List<String> securityOpts) { this.securityOpts = securityOpts; }

    public String getApparmorProfile() { return apparmorProfile; }
    public void setApparmorProfile(String apparmorProfile) { this.apparmorProfile = apparmorProfile; }

    public String getSeccompProfile() { return seccompProfile; }
    public void setSeccompProfile(String seccompProfile) { this.seccompProfile = seccompProfile; }

    public String getClientCertPath() { return clientCertPath; }
    public void setClientCertPath(String clientCertPath) { this.clientCertPath = clientCertPath; }

    public String getClientKeyPath() { return clientKeyPath; }
    public void setClientKeyPath(String clientKeyPath) { this.clientKeyPath = clientKeyPath; }

    public String getCaCertPath() { return caCertPath; }
    public void setCaCertPath(String caCertPath) { this.caCertPath = caCertPath; }

    // ===== Validation Methods =====

    /**
     * Validates the Docker image against security policies.
     * @throws IllegalArgumentException if image fails validation
     */
    public void validateImage(String image) {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("Docker image cannot be empty");
        }

        // Parse image reference
        String[] parts = parseImageReference(image);
        String registry = parts[0];
        String repository = parts[1];
        String tag = parts[2];

        // Check denied registries first (explicit deny)
        if (!deniedRegistries.isEmpty() && deniedRegistries.contains(registry)) {
            throw new IllegalArgumentException("Registry is denied: " + registry);
        }

        // Check allowed registries (if configured)
        if (!allowedRegistries.isEmpty() && !allowedRegistries.contains(registry)) {
            throw new IllegalArgumentException("Registry not allowed: " + registry + ". Allowed: " + allowedRegistries);
        }

        // Check denied image patterns
        for (String pattern : deniedImagePatterns) {
            if (matchGlob(pattern, image)) {
                throw new IllegalArgumentException("Image matches denied pattern: " + pattern);
            }
        }

        // Check tag requirements
        if (requireExplicitTag) {
            if (tag == null || tag.equals("latest")) {
                // Check if "latest" is explicitly allowed
                boolean latestAllowed = allowedTags.contains("latest") || 
                                       deniedImagePatterns.stream().noneMatch(p -> p.contains("latest"));
                if (!latestAllowed) {
                    throw new IllegalArgumentException("Image tag 'latest' is not allowed. Use explicit version tag.");
                }
            }
            // Check allowed tags if configured
            if (!allowedTags.isEmpty() && (tag == null || !allowedTags.contains(tag))) {
                throw new IllegalArgumentException("Image tag not allowed: " + tag + ". Allowed: " + allowedTags);
            }
        }
    }

    /**
     * Validates the Docker host/socket path against allowed list.
     * @throws IllegalArgumentException if host is not allowed
     */
    public void validateDockerHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Docker host cannot be empty");
        }

        // Check against allowed hosts
        if (!allowedDockerHosts.contains(host)) {
            // Allow TCP with TLS if configured
            if (host.startsWith("tcp://") && requireTlsForTcp && host.contains(":2376")) {
                // Acceptable - standard Docker TLS port
                return;
            }
            throw new IllegalArgumentException("Docker host not allowed: " + host + ". Allowed: " + allowedDockerHosts);
        }

        // Validate TLS configuration for TCP connections
        if (host.startsWith("tcp://") && requireTlsForTcp) {
            if (clientCertPath.isBlank() || clientKeyPath.isBlank() || caCertPath.isBlank()) {
                throw new IllegalArgumentException("TLS certificates required for TCP Docker connections. " +
                    "Set clientCertPath, clientKeyPath, and caCertPath");
            }
        }

        // Validate socket path exists and is accessible (Unix/npipe)
        if (host.startsWith("unix://")) {
            String socketPath = host.substring("unix://".length());
            if (!socketPath.startsWith("/var/run/docker") && !socketPath.startsWith("/run/docker")) {
                throw new IllegalArgumentException("Unix socket path must be under /var/run/docker or /run/docker: " + socketPath);
            }
        } else if (host.startsWith("npipe://")) {
            // Docker Desktop exposes dockerDesktopLinuxEngine for the Linux
            // engine and docker_engine for the classic Windows engine. Both
            // names are legitimate; the host must still be in the allow-list
            // checked above.
            if (!host.contains("docker_engine") && !host.contains("dockerDesktopLinuxEngine")) {
                throw new IllegalArgumentException(
                    "Named pipe must reference docker_engine or dockerDesktopLinuxEngine: " + host);
            }
        }
    }

    /**
     * Validates all configuration at startup.
     * @throws IllegalStateException if configuration is invalid
     */
    public void validateConfiguration() {
        // Validate default image FIRST - this catches image policy violations early
        validateImage(dockerImage);

        // Validate Docker host
        validateDockerHost(dockerHost);

        // Validate resource limits
        if (defaultTimeoutSeconds > maxTimeoutSeconds) {
            throw new IllegalStateException("defaultTimeoutSeconds cannot exceed maxTimeoutSeconds");
        }
        if (memoryLimitMb < 256) {
            throw new IllegalStateException("memoryLimitMb must be at least 256MB");
        }
        if (cpuLimitCores < 1) {
            throw new IllegalStateException("cpuLimitCores must be at least 1");
        }
        if (maxProcesses < 10) {
            throw new IllegalStateException("maxProcesses must be at least 10");
        }

        if (workspaceRoots != null && workspaceRoots.stream().anyMatch(root -> root == null || root.isBlank())) {
            throw new IllegalStateException("sandbox workspace roots must not contain blank entries");
        }

        if (workspaceVolume != null && !workspaceVolume.isBlank()) {
            if (!workspaceVolume.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}")) {
                throw new IllegalStateException("sandbox workspace volume name is invalid: " + workspaceVolume);
            }
            if (workspaceVolumeRoot == null || !workspaceVolumeRoot.startsWith("/")) {
                throw new IllegalStateException("sandbox workspace volume root must be an absolute container path");
            }
        }

        // Validate security options
        if (securityOpts == null || securityOpts.isEmpty()) {
            throw new IllegalStateException("securityOpts must include 'no-new-privileges:true'");
        }
        boolean hasNoNewPrivs = securityOpts.stream().anyMatch(opt -> opt.contains("no-new-privileges"));
        if (!hasNoNewPrivs) {
            throw new IllegalStateException("securityOpts must include 'no-new-privileges:true'");
        }
    }

    /**
     * Parses a Docker image reference into registry, repository, tag.
     * Supports: registry/repo:tag, repo:tag, registry/repo@sha256:digest
     */
    private String[] parseImageReference(String image) {
        String registry = "docker.io"; // default
        String repository;
        String tag = "latest"; // default

        // Check for digest (@sha256:...)
        int digestIdx = image.indexOf('@');
        if (digestIdx > 0) {
            // Digest reference - tag is the digest
            tag = image.substring(digestIdx + 1);
            image = image.substring(0, digestIdx);
        }

        // Split by / to check for registry
        String[] slashParts = image.split("/");
        if (slashParts.length >= 2 && (slashParts[0].contains(".") || slashParts[0].contains(":"))) {
            // First part looks like a registry (contains . or :)
            registry = slashParts[0];
            repository = String.join("/", java.util.Arrays.copyOfRange(slashParts, 1, slashParts.length));
        } else {
            // No explicit registry - use docker.io
            repository = image;
        }

        // Split repository:tag
        int colonIdx = repository.lastIndexOf(':');
        if (colonIdx > 0 && !repository.substring(colonIdx).contains("/")) {
            tag = repository.substring(colonIdx + 1);
            repository = repository.substring(0, colonIdx);
        }

        return new String[]{registry, repository, tag};
    }

    /**
     * Simple glob pattern matching.
     */
    private boolean matchGlob(String pattern, String input) {
        // Convert glob to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".");
        return input.matches(regex);
    }
}
