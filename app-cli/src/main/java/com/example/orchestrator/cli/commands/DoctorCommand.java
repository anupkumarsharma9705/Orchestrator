package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.TerminalSupport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnose missing prerequisites for running the orchestrator.
 */
@Command(
    name = "doctor",
    description = "Diagnose missing prerequisites for running the orchestrator",
    mixinStandardHelpOptions = true
)
public class DoctorCommand implements Callable<Integer> {

    // Pre-compiled patterns (MV-03: avoid duplicate compilation)
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("version \"(\\d+)\\.(\\d+)");
    private static final Pattern MAVEN_VERSION_PATTERN = Pattern.compile("Apache Maven (\\d+)\\.(\\d+)");
    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("git version (\\d+)\\.(\\d+)");
    private static final Pattern DOCKER_VERSION_PATTERN = Pattern.compile("Docker version (\\d+(?:\\.\\d+)*)");

    @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
    private String outputFormat = "table";

    @ParentCommand
    private CompanionCli parent;

    @Override
    public Integer call() throws Exception {
        CliConfig config = new ConfigManager().load();
        OutputFormatter formatter = new OutputFormatter(outputFormat);

        System.out.println(Ansi.bold(Ansi.CYAN + TerminalSupport.heading("DOCTOR - Prerequisite Checks")));
        System.out.println();

        DoctorResults results = new DoctorResults();
        
        // Run all checks
        checkJava(results);
        checkMaven(results);
        checkDocker(results);
        checkGit(results);
        boolean orchestratorHealthy = checkOrchestratorServer(results, config);
        checkWorkspace(results);
        checkDiskSpace(results);
        checkPort(results, config, orchestratorHealthy);
        checkMemory(results);

        // Display results
        displayResults(results, formatter);

        return results.hasFailures() ? 1 : 0;
    }

    private void checkJava(DoctorResults results) {
        try {
            String versionOutput = runCommand("java", "-version");
            // java -version outputs to stderr
            String version = extractJavaVersion(versionOutput);
            
            if (version != null) {
                int majorVersion = parseJavaMajorVersion(version);
                if (majorVersion >= 21) {
                    results.addCheck("Java 21+", true, "Java " + version + " found", "OK");
                } else {
                    results.addCheck("Java 21+", false, "Java " + version + " found (need 21+)", "Install Java 21 or later");
                }
            } else {
                results.addCheck("Java 21+", false, "Could not parse Java version", "Ensure 'java' is in PATH");
            }
        } catch (Exception e) {
            results.addCheck("Java 21+", false, "Java not found", "Install Java 21+ and add to PATH");
        }
    }

    private void checkMaven(DoctorResults results) {
        try {
            String versionOutput = runCommand("mvn", "-version");
            Matcher matcher = MAVEN_VERSION_PATTERN.matcher(versionOutput);
            
            if (matcher.find()) {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                
                if (major > 3 || (major == 3 && minor >= 9)) {
                    results.addCheck("Maven 3.9+", true, "Maven " + major + "." + minor + " found", "OK");
                } else {
                    results.addCheck("Maven 3.9+", false, "Maven " + major + "." + minor + " found (need 3.9+)", "Upgrade Maven to 3.9+");
                }
            } else {
                results.addCheck("Maven 3.9+", false, "Could not parse Maven version", "Ensure Maven is installed and in PATH");
            }
        } catch (Exception e) {
            results.addCheck("Maven 3.9+", false, "Maven not found", "Install Maven 3.9+ and add to PATH");
        }
    }

    private void checkDocker(DoctorResults results) {
        // `docker --version` only needs the CLI and works even when the daemon is down.
        String versionOutput;
        try {
            versionOutput = runCommand("docker", "--version");
        } catch (Exception e) {
            results.addCheck("Docker 27+", false, "Docker not found", "Install Docker 27+ and add to PATH");
            return;
        }

        Matcher matcher = DOCKER_VERSION_PATTERN.matcher(versionOutput);
        if (!matcher.find()) {
            results.addCheck("Docker 27+", false, "Could not parse Docker version: " + firstLine(versionOutput),
                    "Reinstall Docker 27+");
            return;
        }

        String version = matcher.group(1);
        int major;
        try {
            major = Integer.parseInt(version.split("\\.")[0]);
        } catch (NumberFormatException e) {
            results.addCheck("Docker 27+", false, "Could not parse Docker version: " + version, "Reinstall Docker 27+");
            return;
        }

        if (major < 27) {
            results.addCheck("Docker 27+", false, "Docker " + version + " installed (need 27+)", "Upgrade Docker to 27+");
            return;
        }

        // Verify the daemon is actually reachable.
        try {
            CommandResult info = runCommandResult("docker", "system", "info", "--format", "{{.ServerVersion}}");
            String serverVersion = info.output().trim();
            if (info.exitCode() == 0 && !serverVersion.isEmpty()) {
                results.addCheck("Docker 27+", true,
                        "Docker " + version + " (daemon " + firstLine(serverVersion) + ")", "OK");
            } else {
                results.addCheck("Docker 27+", false,
                        "Docker " + version + " installed but daemon not reachable", "Start Docker daemon");
            }
        } catch (Exception e) {
            results.addCheck("Docker 27+", false,
                    "Docker " + version + " installed but daemon not reachable", "Start Docker daemon");
        }
    }

    private void checkGit(DoctorResults results) {
        try {
            String versionOutput = runCommand("git", "--version");
            Matcher matcher = GIT_VERSION_PATTERN.matcher(versionOutput);
            
            if (matcher.find()) {
                results.addCheck("Git", true, "Git " + matcher.group(0).split(" ")[2] + " found", "OK");
            } else {
                results.addCheck("Git", false, "Could not parse Git version", "Ensure Git is in PATH");
            }
        } catch (Exception e) {
            results.addCheck("Git", false, "Git not found", "Install Git and add to PATH");
        }
    }

    private boolean checkOrchestratorServer(DoctorResults results, CliConfig config) {
        try {
            OrchestratorClient client = new OrchestratorClient(config);
            OrchestratorClient.HealthResponse health = client.healthCheck().block();
            
            if (health != null && "UP".equalsIgnoreCase(health.status())) {
                results.addCheck("Orchestrator Server", true, "Server reachable at " + config.serverUrl(), "OK");
                return true;
            } else {
                results.addCheck("Orchestrator Server", false, "Server at " + config.serverUrl() + " is not healthy", "Start orchestrator server");
                return false;
            }
        } catch (Exception e) {
            String cause = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            results.addCheck("Orchestrator Server", false,
                    "Cannot connect to " + config.serverUrl() + ": " + cause,
                    "Start orchestrator server at " + config.serverUrl()
                            + " or run scripts/dev-up");
            return false;
        }
    }

    private void checkWorkspace(DoctorResults results) {
        try {
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            boolean writable = Files.isWritable(Paths.get("."));
            
            if (writable) {
                results.addCheck("Workspace writable", true, "Current directory is writable", "OK");
            } else {
                results.addCheck("Workspace writable", false, "Current directory is not writable", "Check permissions or run from writable directory");
            }
        } catch (Exception e) {
            results.addCheck("Workspace writable", false, "Could not check workspace", "Check directory permissions");
        }
    }

    private void checkDiskSpace(DoctorResults results) {
        try {
            Path cwd = Paths.get(".").toAbsolutePath().normalize();
            long freeSpace = Files.getFileStore(Paths.get(".")).getUsableSpace();
            long freeGB = freeSpace / (1024 * 1024 * 1024);
            
            if (freeGB >= 2) {
                results.addCheck("Disk space > 2GB", true, freeGB + " GB free", "OK");
            } else {
                results.addCheck("Disk space > 2GB", false, "Only " + freeGB + " GB free (need 2GB+)", "Free up disk space");
            }
        } catch (Exception e) {
            results.addCheck("Disk space > 2GB", false, "Could not check disk space", "Check disk permissions");
        }
    }

    private void checkPort(DoctorResults results, CliConfig config, boolean orchestratorHealthy) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 8443), 1000);

            if (orchestratorHealthy && isLocalOrchestratorEndpoint(config)) {
                // The port is in use because our own orchestrator server is listening on it.
                results.addCheck("Port 8443", true,
                        "Port 8443 is used by the running orchestrator server", "OK");
            } else {
                results.addCheck("Port 8443", false,
                        "Port 8443 is already in use by another process",
                        "Stop the process using port 8443 or configure a different port");
            }
        } catch (Exception e) {
            // Connection failed = port is free
            results.addCheck("Port 8443", true, "Port 8443 is available", "OK");
        }
    }

    /**
     * Returns true when the configured server URL points at this machine's default
     * orchestrator port, so an occupied port 8443 is expected rather than a problem.
     */
    static boolean isLocalOrchestratorEndpoint(CliConfig config) {
        try {
            URI uri = URI.create(config.serverUrl());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            boolean localHost = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || "[::1]".equals(host);
            return localHost && uri.getPort() == 8443;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void checkMemory(DoctorResults results) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long maxGB = heapMax / (1024 * 1024 * 1024);
        long usedGB = heapUsed / (1024 * 1024 * 1024);
        
        if (maxGB >= 1) {
            results.addCheck("JVM Heap >= 1GB", true, "Max heap: " + maxGB + " GB (used: " + usedGB + " GB)", "OK");
        } else {
            results.addCheck("JVM Heap >= 1GB", false, "Max heap: " + maxGB + " GB (need 1GB+)", "Increase JVM heap with -Xmx1g or higher");
        }
    }

    private record CommandResult(int exitCode, String output) {
    }

    private String runCommand(String... command) throws IOException, InterruptedException {
        CommandResult result = runCommandResult(command);
        if (result.exitCode() != 0) {
            throw new IOException("Command failed (exit " + result.exitCode() + "): " + firstLine(result.output()));
        }
        return result.output();
    }

    private CommandResult runCommandResult(String... command) throws IOException, InterruptedException {
        // On Windows, use cmd /c for .cmd/.bat files (like mvn.cmd, docker.cmd)
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> fullCommand = new ArrayList<>();
        if (isWindows) {
            fullCommand.add("cmd");
            fullCommand.add("/c");
        }
        fullCommand.addAll(List.of(command));

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.redirectErrorStream(true);
        pb.environment().putAll(System.getenv());
        Process process = pb.start();

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }

        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        return new CommandResult(process.exitValue(), output);
    }

    private String firstLine(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int newline = trimmed.indexOf('\n');
        return newline >= 0 ? trimmed.substring(0, newline).trim() : trimmed;
    }

    private String extractJavaVersion(String output) {
        // java -version outputs to stderr, but we merged stderr to stdout
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(output);
        if (matcher.find()) {
            return matcher.group(1) + "." + matcher.group(2);
        }
        return null;
    }

    private int parseJavaMajorVersion(String version) {
        // Handle "1.8" -> 8, "11" -> 11, "17" -> 17, "21" -> 21
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.split("\\.")[1]);
        }
        return Integer.parseInt(version.split("\\.")[0]);
    }

    private void displayResults(DoctorResults results, OutputFormatter formatter) {
        System.out.println();
        System.out.println(Ansi.bold(Ansi.CYAN + TerminalSupport.heading("RESULTS")));
        System.out.println();

        java.util.List<String> headers = java.util.List.of("Check", "Status", "Details", "Remediation");
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();

        for (DoctorResults.Check check : results.checks) {
            String status = check.passed
                ? Ansi.success(TerminalSupport.CHECK + " PASS")
                : Ansi.error(TerminalSupport.CROSS + " FAIL");
            rows.add(java.util.List.of(
                check.name,
                status,
                check.details,
                check.remediation
            ));
        }

        System.out.println(new OutputFormatter("table").formatTable(
            java.util.List.of("Check", "Status", "Details", "Remediation"),
            rows
        ));

        System.out.println();
        if (results.hasFailures()) {
            System.out.println(Ansi.error(TerminalSupport.CROSS
                + " Some checks failed. Please address the issues above."));
        } else {
            System.out.println(Ansi.success(TerminalSupport.CHECK
                + " All checks passed! You're ready to use the orchestrator."));
        }
    }

    // Helper classes
    private static class DoctorResults {
        final java.util.List<Check> checks = new java.util.ArrayList<>();

        void addCheck(String name, boolean passed, String details, String remediation) {
            checks.add(new Check(name, passed, details, remediation));
        }

        boolean hasFailures() {
            return checks.stream().anyMatch(c -> !c.passed);
        }

        static class Check {
            final String name;
            final boolean passed;
            final String details;
            final String remediation;

            Check(String name, boolean passed, String details, String remediation) {
                this.name = name;
                this.passed = passed;
                this.details = details;
                this.remediation = remediation;
            }
        }
    }
}