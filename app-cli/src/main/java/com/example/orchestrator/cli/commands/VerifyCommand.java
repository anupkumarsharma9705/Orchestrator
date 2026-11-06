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
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Verify an existing Spring Boot project.
 */
@Command(
    name = "verify",
    description = "Verify an existing Spring Boot project",
    mixinStandardHelpOptions = true
)
public class VerifyCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the Spring Boot project folder")
    private String folder;

    @Option(names = {"--project-name"}, description = "Project name for the verification task")
    private String projectName;

    @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
    private String outputFormat = "table";

    @ParentCommand
    private CompanionCli parent;

    @Override
    public Integer call() throws Exception {
        Path projectPath = resolveProjectPath();
        if (!Files.exists(projectPath)) {
            System.err.println(Ansi.error(" Project folder does not exist: " + projectPath));
            return 1;
        }
        if (!Files.isDirectory(projectPath)) {
            System.err.println(Ansi.error(" Path is not a directory: " + projectPath));
            return 1;
        }

        // Load config
        CliConfig config = new ConfigManager().load();

        // Initialize client and formatter
        OrchestratorClient client = new OrchestratorClient(config);
        OutputFormatter formatter = new OutputFormatter(outputFormat);

        System.out.println(Ansi.bold(Ansi.CYAN + TerminalSupport.ARROW + " Verifying project: ")
            + projectPath.getFileName());
        System.out.println(Ansi.dim("Path: " + projectPath.toAbsolutePath()));
        System.out.println();

        // Health check
        System.out.println(Ansi.info(" Checking server health..."));
        try {
            OrchestratorClient.HealthResponse health = client.healthCheck().block();
            if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                System.err.println(Ansi.error(" Server is not healthy"));
                return 1;
            }
            System.out.println(Ansi.success(" Server is healthy"));
        } catch (Exception e) {
            System.err.println(Ansi.error(" Cannot connect to server: " + e.getMessage()));
            return 1;
        }

        // Submit verification task
        String taskProjectName = projectName != null ? projectName : projectPath.getFileName().toString();
        OrchestratorClient.CreateTaskRequest request = new OrchestratorClient.CreateTaskRequest(
            taskProjectName, // projectId
            "Verify existing Spring Boot project at " + projectPath.toAbsolutePath(), // request
            "cli-user" // requester
        );

        System.out.println(Ansi.info(" Submitting verification task..."));
        OrchestratorClient.TaskResponse task = client.submitTask(request).block();
        if (task == null) {
            System.err.println(Ansi.error(" Failed to submit verification task"));
            return 1;
        }

        System.out.println(Ansi.success(" Verification task created: " + task.taskId()));
        System.out.println();

        // Poll for completion
        OrchestratorClient.TaskResponse finalTask = pollForCompletion(client, task.taskId());

        // Display results
        displayResults(client, finalTask, formatter);

        return "COMPLETED".equals(finalTask.status()) ? 0 : 1;
    }

    private OrchestratorClient.TaskResponse pollForCompletion(OrchestratorClient client, String taskId) {
        System.out.println(Ansi.info(" Running verification..."));

        while (true) {
            OrchestratorClient.TaskResponse task = client.getTask(taskId).block();
            if (task == null) throw new RuntimeException("Task not found");

            if ("COMPLETED".equals(task.status())) {
                System.out.println(Ansi.success(" Verification completed"));
                return task;
            }

            if ("FAILED".equals(task.status())) {
                System.out.println(Ansi.error(" Verification failed: " + task.error()));
                return task;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private void displayResults(OrchestratorClient client, OrchestratorClient.TaskResponse task, OutputFormatter formatter) {
        System.out.println();
        System.out.println(Ansi.bold(Ansi.CYAN + TerminalSupport.heading("VERIFICATION RESULTS")));
        System.out.println();

        if ("COMPLETED".equals(task.status())) {
            System.out.println(Ansi.success(" Verification passed"));
        } else {
            System.out.println(Ansi.error(" Verification failed"));
            if (task.error() != null) {
                System.out.println(Ansi.dim("Error: " + task.error()));
            }
        }

        // Try to fetch and display findings
        try {
            var artifacts = client.getArtifacts(task.taskId()).block();
            // Note: Need to pass client properly - simplified for now
        } catch (Exception e) {
            // Ignore artifact fetch errors
        }
    }

    private Path resolveProjectPath() {
        if (folder != null && !folder.isBlank()) {
            return Paths.get(folder).toAbsolutePath().normalize();
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }
}