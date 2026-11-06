package com.example.orchestrator.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the SandboxService security features.
 */
@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {

    private SandboxConfig config;
    private SandboxService sandboxService;

    private SandboxConfig createValidTestConfig() {
        SandboxConfig c = new SandboxConfig();
        c.setDockerImage("orchestrator/sandbox:1.0.0");
        c.setDockerHost("unix:///var/run/docker.sock");
        c.setAllowedRegistries(List.of("docker.io", "ghcr.io"));
        c.setDeniedRegistries(List.of("malicious.registry.com"));
        c.setRequireExplicitTag(true);
        c.setAllowedTags(List.of("1.0.0", "1.0.1", "stable"));
        c.setDeniedImagePatterns(List.of("*:latest", "*:dev-*", "malicious/*"));
        c.setAllowedDockerHosts(List.of("unix:///var/run/docker.sock", "unix:///run/docker.sock", "npipe:////./pipe/docker_engine"));
        c.setRequireTlsForTcp(true);
        c.setAllowedCapabilities(List.of());
        c.setDeniedCapabilities(List.of(
            "CAP_SYS_ADMIN", "CAP_SYS_RESOURCE", "CAP_SYS_MODULE",
            "CAP_SYS_RAWIO", "CAP_SYS_PACCT", "CAP_SYS_NICE",
            "CAP_SYSLOG", "CAP_DAC_READ_SEARCH", "CAP_LINUX_IMMUTABLE",
            "CAP_MAC_ADMIN", "CAP_MAC_OVERRIDE", "CAP_BPF",
            "CAP_PERFMON", "CAP_CHECKPOINT_RESTORE"
        ));
        c.setSecurityOpts(List.of("no-new-privileges:true"));
        c.setApparmorProfile("docker-default");
        c.setSeccompProfile("default");
        c.setDefaultTimeoutSeconds(300);
        c.setMaxTimeoutSeconds(3600);
        c.setMemoryLimitMb(2048);
        c.setCpuLimitCores(2);
        c.setMaxProcesses(100);
        c.setMavenCacheVolume("orchestrator-maven-cache");
        c.setWorkspaceMountPath("/workspace");
        c.setEnableNetwork(false);
        return c;
    }

    @Test
    void config_hasDefaults() {
        SandboxConfig config = new SandboxConfig();
        assertThat(config.getDockerImage()).isEqualTo("orchestrator/sandbox:1.0.0");
        assertThat(config.getDefaultTimeoutSeconds()).isEqualTo(300);
        assertThat(config.getMemoryLimitMb()).isEqualTo(2048);
        assertThat(config.getCpuLimitCores()).isEqualTo(2);
        assertThat(config.getMaxProcesses()).isEqualTo(100);
        assertThat(config.isEnableNetwork()).isFalse();
        assertThat(config.getWorkspaceMountPath()).isEqualTo("/workspace");
        // New security defaults
        assertThat(config.getDeniedImagePatterns()).contains("*:latest");
        assertThat(config.getSecurityOpts()).contains("no-new-privileges:true");
        assertThat(config.getApparmorProfile()).isEqualTo("docker-default");
        assertThat(config.getSeccompProfile()).isEqualTo("default");
        assertThat(config.isRequireExplicitTag()).isTrue();
        assertThat(config.isRequireTlsForTcp()).isTrue();
    }

    @Test
    void validateImage_acceptsValidImage() {
        config = createValidTestConfig();
        // Should not throw - using tags from allowedTags: 1.0.0, 1.0.1, stable
        config.validateImage("docker.io/library/maven:1.0.0");
        config.validateImage("ghcr.io/org/repo:1.0.1");
        config.validateImage("orchestrator/sandbox:stable");
    }

    @Test
    void validateImage_rejectsLatestTag() {
        config = createValidTestConfig();
        assertThatThrownBy(() -> config.validateImage("orchestrator/sandbox:latest"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("denied pattern");
    }

    @Test
    void validateImage_rejectsDeniedRegistry() {
        config = createValidTestConfig();
        assertThatThrownBy(() -> config.validateImage("malicious.registry.com/evil:1.0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Registry is denied");
    }

    @Test
    void validateImage_rejectsDeniedPattern() {
        config = createValidTestConfig();
        assertThatThrownBy(() -> config.validateImage("orchestrator/sandbox:dev-123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("denied pattern");
    }

    @Test
    void validateImage_allowsAllowedTag() {
        config = createValidTestConfig();
        // "stable" is in allowedTags
        config.validateImage("orchestrator/sandbox:stable");
    }

    @Test
    void validateImage_rejectsDisallowedTag() {
        config = createValidTestConfig();
        // "2.0.0" not in allowedTags
        assertThatThrownBy(() -> config.validateImage("orchestrator/sandbox:2.0.0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tag not allowed");
    }

    @Test
    void defaultDockerHost_isInDefaultAllowList() {
        SandboxConfig config = new SandboxConfig();
        // Must not throw: a platform default that is not allow-listed makes
        // SandboxService fail startup on that platform.
        config.validateDockerHost(config.getDockerHost());
    }

    @Test
    void validateDockerHost_acceptsAllowedUnixSocket() {
        config = createValidTestConfig();
        config.validateDockerHost("unix:///var/run/docker.sock");
        config.validateDockerHost("unix:///run/docker.sock");
    }

    @Test
    void validateDockerHost_acceptsAllowedNamedPipe() {
        config = createValidTestConfig();
        config.setAllowedDockerHosts(List.of(
            "npipe:////./pipe/docker_engine",
            "npipe:////./pipe/dockerDesktopLinuxEngine"));
        config.validateDockerHost("npipe:////./pipe/docker_engine");
        config.validateDockerHost("npipe:////./pipe/dockerDesktopLinuxEngine");
    }

    @Test
    void validateDockerHost_rejectsUnknownUnixSocket() {
        config = createValidTestConfig();
        assertThatThrownBy(() -> config.validateDockerHost("unix:///tmp/fake.sock"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Docker host not allowed");
    }

    @Test
    void validateDockerHost_rejectsNonStandardTcpPort() {
        config = createValidTestConfig();
        assertThatThrownBy(() -> config.validateDockerHost("tcp://localhost:2375"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Docker host not allowed");
    }

    @Test
    void validateDockerHost_acceptsStandardTlsPort() {
        config = createValidTestConfig();
        // Should accept standard Docker TLS port
        config.validateDockerHost("tcp://localhost:2376");
    }

    @Test
    void validateConfiguration_passesForValidConfig() {
        config = createValidTestConfig();
        // Should not throw
        config.validateConfiguration();
    }

    @Test
    void validateConfiguration_rejectsInvalidDefaults() {
        SandboxConfig invalidConfig = new SandboxConfig();
        invalidConfig.setDockerImage("orchestrator/sandbox:latest"); // denied
        invalidConfig.setSecurityOpts(List.of()); // missing no-new-privileges
        // Image validation runs first and throws IllegalArgumentException
        assertThatThrownBy(invalidConfig::validateConfiguration)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Image matches denied pattern");
    }

    @Test
    void validateConfiguration_rejectsTimeoutMismatch() {
        config = createValidTestConfig();
        config.setDefaultTimeoutSeconds(4000);
        config.setMaxTimeoutSeconds(3600);
        assertThatThrownBy(config::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("defaultTimeoutSeconds cannot exceed maxTimeoutSeconds");
    }

    @Test
    void validateWorkspacePath_allowsValidPath(@TempDir Path tempDir) throws IOException {
        config = createValidTestConfig();
        config.setWorkspaceRoots(List.of(tempDir.toString()));
        sandboxService = new SandboxService(config);

        Path testWorkspace = tempDir.resolve("test-project");
        Files.createDirectories(testWorkspace);

        // Use reflection to test private method
        Path validated = invokeValidateWorkspacePath(testWorkspace);
        assertThat(validated).isNotNull();
        assertThat(validated).isEqualTo(testWorkspace.toRealPath());
    }

    @Test
    void validateWorkspacePath_rejectsPathOutsideConfiguredRoots(@TempDir Path tempDir) throws IOException {
        Path allowedRoot = tempDir.resolve("allowed");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(allowedRoot);
        Files.createDirectories(outside);

        config = createValidTestConfig();
        config.setWorkspaceRoots(List.of(allowedRoot.toString()));
        sandboxService = new SandboxService(config);

        assertThat(invokeValidateWorkspacePath(outside)).isNull();
    }

    @Test
    void validateWorkspacePath_rejectsSymlinkEscape(@TempDir Path tempDir) throws IOException {
        Path allowedRoot = tempDir.resolve("allowed");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(allowedRoot);
        Files.createDirectories(outside);
        Path link = allowedRoot.resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Symlinks need privileges on Windows; nothing to assert.
        }

        config = createValidTestConfig();
        config.setWorkspaceRoots(List.of(allowedRoot.toString()));
        sandboxService = new SandboxService(config);

        assertThat(invokeValidateWorkspacePath(link)).isNull();
    }

    @Test
    void validateWorkspacePath_rejectsPathOutsideBase(@TempDir Path tempDir) {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        // Create a temp directory outside the allowed base
        Path outsidePath = tempDir.resolve("outside");
        assertThat(invokeValidateWorkspacePath(outsidePath)).isNull();
    }

    @Test
    void validateWorkspacePath_rejectsPathTraversal(@TempDir Path tempDir) {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        Path allowedBase = Paths.get(System.getProperty("user.home"), ".orchestrator", "workspaces").toAbsolutePath().normalize();
        Path traversalPath = allowedBase.resolve("../etc/passwd");
        
        assertThat(invokeValidateWorkspacePath(traversalPath)).isNull();
    }

    @Test
    void validateWorkspacePath_rejectsWindowsPathTraversal(@TempDir Path tempDir) {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        Path allowedBase = Paths.get(System.getProperty("user.home"), ".orchestrator", "workspaces").toAbsolutePath().normalize();
        Path traversalPath = allowedBase.resolve("..\\..\\windows\\system32");
        
        assertThat(invokeValidateWorkspacePath(traversalPath)).isNull();
    }

    @Test
    void parseCommandSecure_allowsSimpleCommand() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        String[] result = invokeParseCommandSecure("mvn clean compile");
        assertThat(result).isNotNull();
        assertThat(result).containsExactly("mvn", "clean", "compile");
    }

    @Test
    void parseCommandSecure_allowsQuotedArguments() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        String[] result = invokeParseCommandSecure("echo \"hello world\"");
        assertThat(result).isNotNull();
        assertThat(result).containsExactly("echo", "hello world");
    }

    @Test
    void parseCommandSecure_rejectsShellMetacharacters() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        assertThat(invokeParseCommandSecure("mvn clean; rm -rf /")).isNull();
        assertThat(invokeParseCommandSecure("mvn clean && rm -rf /")).isNull();
        assertThat(invokeParseCommandSecure("mvn clean | cat")).isNull();
        assertThat(invokeParseCommandSecure("mvn clean `rm -rf /`")).isNull();
        assertThat(invokeParseCommandSecure("mvn clean $(rm -rf /)")).isNull();
    }

    @Test
    void parseCommandSecure_rejectsPathTraversalInCommand() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        assertThat(invokeParseCommandSecure("../mvn clean")).isNull();
        // ".." as an argument (not the command itself) is allowed - only the first arg is validated
        assertThat(invokeParseCommandSecure("mvn clean ..")).isNotNull();
    }

    @Test
    void parseCommandSecure_rejectsTooLongCommand() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        String longCommand = "mvn " + "a ".repeat(3000);
        assertThat(invokeParseCommandSecure(longCommand)).isNull();
    }

    @Test
    void parseCommandSecure_handlesEmptyCommand() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        assertThat(invokeParseCommandSecure("")).isNull();
        assertThat(invokeParseCommandSecure("   ")).isNull();
        assertThat(invokeParseCommandSecure(null)).isNull();
    }

    @Test
    void sanitizeForLog_masksSecrets() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        String input = "mvn clean -Dpassword=secret123 -Dtoken=abc123";
        String result = invokeSanitizeForLog(input);
        
        assertThat(result).contains("password=***");
        assertThat(result).contains("token=***");
        assertThat(result).doesNotContain("secret123");
        assertThat(result).doesNotContain("abc123");
    }

    @Test
    void sanitizeForLog_truncatesLongInput() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);
        
        String longInput = "mvn " + "a ".repeat(200);
        String result = invokeSanitizeForLog(longInput);
        
        assertThat(result).endsWith("...[truncated]");
        // 200 chars + "...[truncated]" (14 chars) = 214
        assertThat(result.length()).isLessThanOrEqualTo(214);
    }

    @Test
    void validateArguments_acceptsPlainArgv() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);

        assertThat(invokeValidateArguments(List.of("mvn", "-B", "verify")))
            .containsExactly("mvn", "-B", "verify");
        assertThat(invokeValidateArguments(List.of("semgrep", "--config=auto", "--json", ".")))
            .containsExactly("semgrep", "--config=auto", "--json", ".");
    }

    @Test
    void validateArguments_rejectsPathExecutableAndEmptyLists() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);

        assertThat(invokeValidateArguments(null)).isNull();
        assertThat(invokeValidateArguments(List.of())).isNull();
        assertThat(invokeValidateArguments(List.of("../mvn", "clean"))).isNull();
        assertThat(invokeValidateArguments(List.of("/bin/sh", "-c", "id"))).isNull();
        assertThat(invokeValidateArguments(List.of("mvn", "a\u0000b"))).isNull();
    }

    @Test
    void validateArguments_rejectsOversizedArgv() {
        config = new SandboxConfig();
        sandboxService = new SandboxService(config);

        String huge = "a".repeat(70_000);
        assertThat(invokeValidateArguments(List.of("mvn", huge))).isNull();
    }

    // Helper methods to invoke private methods via reflection
    @SuppressWarnings("unchecked")
    private List<String> invokeValidateArguments(List<String> arguments) {
        try {
            java.lang.reflect.Method method = SandboxService.class.getDeclaredMethod("validateArguments", List.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(sandboxService, arguments);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path invokeValidateWorkspacePath(Path path) {
        try {
            java.lang.reflect.Method method = SandboxService.class.getDeclaredMethod("validateWorkspacePath", Path.class);
            method.setAccessible(true);
            return (Path) method.invoke(sandboxService, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String[] invokeParseCommandSecure(String command) {
        try {
            java.lang.reflect.Method method = SandboxService.class.getDeclaredMethod("parseCommandSecure", String.class);
            method.setAccessible(true);
            return (String[]) method.invoke(sandboxService, command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeSanitizeForLog(String input) {
        try {
            java.lang.reflect.Method method = SandboxService.class.getDeclaredMethod("sanitizeForLog", String.class);
            method.setAccessible(true);
            return (String) method.invoke(sandboxService, input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}