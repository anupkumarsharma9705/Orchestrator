package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Manage projects.
 */
@Command(
    name = "project",
    description = "Manage projects",
    mixinStandardHelpOptions = true,
    subcommands = {
        ProjectCommand.List.class,
        ProjectCommand.Register.class
    }
)
public class ProjectCommand implements Callable<Integer> {

    @ParentCommand
    private CompanionCli parent;

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'companion project --help' to see subcommands");
        return 0;
    }

    /**
     * List all registered projects.
     */
    @Command(name = "list", description = "List all registered projects")
    public static class List implements Callable<Integer> {
        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());
            OutputFormatter formatter = new OutputFormatter(outputFormat);

            System.out.println(Ansi.info(" Fetching projects..."));

            try {
                // First check if server is reachable
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    System.err.println(Ansi.dim(" Or use --offline flag for local operations"));
                    return 1;
                }

                // Note: listProjects endpoint doesn't exist yet on server
                System.out.println(Ansi.warning(" Project listing not yet implemented on server"));
                System.out.println(Ansi.dim(" Use 'companion project register' to create a new project"));
                return 0;
            } catch (Exception e) {
                // Check if it's a connection error
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    System.err.println(Ansi.dim(" Or use --offline flag for local operations"));
                } else {
                    System.err.println(Ansi.error(" Failed to list projects: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * Register a new project.
     */
    @Command(name = "register", description = "Register a new project")
    public static class Register implements Callable<Integer> {
        @Parameters(index = "0", description = "Project name")
        private String name;

        @Parameters(index = "1", description = "Project root path")
        private String path;

        @Option(names = {"--description"}, description = "Project description")
        private String description;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            Path projectPath = resolveProjectPath();
            if (!Files.exists(projectPath)) {
                System.err.println(Ansi.error(" Project path does not exist: " + projectPath));
                return 1;
            }
            if (!Files.isDirectory(projectPath)) {
                System.err.println(Ansi.error(" Path is not a directory: " + projectPath));
                return 1;
            }

            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());
            OutputFormatter formatter = new OutputFormatter(outputFormat);

            System.out.println(Ansi.info(" Registering project: " + name));
            System.out.println(Ansi.dim("Path: " + projectPath.toAbsolutePath()));

            OrchestratorClient.ProjectRequest request = new OrchestratorClient.ProjectRequest(
                name, projectPath.toAbsolutePath().toString(), description != null ? description : ""
            );

            try {
                OrchestratorClient.ProjectResponse response = client.registerProject(request).block();
                if (response == null) {
                    System.err.println(Ansi.error(" Failed to register project"));
                    return 1;
                }

                System.out.println(Ansi.success(" Project registered successfully!"));
                System.out.println(Ansi.dim("Project ID: " + response.projectId()));
                System.out.println(Ansi.dim("Created at: " + response.createdAt()));

                // Show as table
                java.util.List<String> headers = java.util.List.of("Field", "Value");
                java.util.List<java.util.List<String>> rows = java.util.List.of(
                    java.util.List.of("Project ID", response.projectId()),
                    java.util.List.of("Name", response.name()),
                    java.util.List.of("Root Path", response.rootPath()),
                    java.util.List.of("Created At", response.createdAt())
                );
                System.out.println(new OutputFormatter(outputFormat).formatTable(headers, rows));

                return 0;
            } catch (Exception e) {
                System.err.println(Ansi.error(" Failed to register project: " + e.getMessage()));
                return 1;
            }
        }

        private Path resolveProjectPath() {
            if (path != null && !path.isBlank()) {
                return Paths.get(path).toAbsolutePath().normalize();
            }
            return Paths.get(".").toAbsolutePath().normalize();
        }
    }
}