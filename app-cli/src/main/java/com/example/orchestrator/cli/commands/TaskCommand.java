package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Manage and inspect tasks.
 */
@Command(
    name = "task",
    description = "Manage and inspect tasks",
    mixinStandardHelpOptions = true,
    subcommands = {
        TaskCommand.Get.class,
        TaskCommand.List.class,
        TaskCommand.Approve.class,
        TaskCommand.Reject.class,
        TaskCommand.Logs.class,
        TaskCommand.Artifacts.class
    }
)
public class TaskCommand implements Callable<Integer> {

    @ParentCommand
    private CompanionCli parent;

    @Override
    public Integer call() throws Exception {
        System.out.println("Use 'companion task --help' to see subcommands");
        return 0;
    }

    private OrchestratorClient getClient() {
        return new OrchestratorClient(new ConfigManager().load());
    }

    /**
     * Get task details.
     */
    @Command(name = "get", description = "Get task details")
    public static class Get implements Callable<Integer> {
        @Parameters(index = "0", description = "Task ID")
        private String taskId;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());
            OutputFormatter formatter = new OutputFormatter(outputFormat);

            System.out.println(Ansi.info(" Fetching task: " + taskId));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.TaskResponse task = client.getTask(taskId).block();
                if (task == null) {
                    System.err.println(Ansi.error(" Task not found: " + taskId));
                    return 1;
                }

                System.out.println(Ansi.success(" Task found: " + task.taskId()));
                System.out.println();

                java.util.List<String> headers = java.util.List.of("Field", "Value");
                java.util.List<java.util.List<String>> rows = java.util.List.of(
                    java.util.List.of("Task ID", task.taskId()),
                    java.util.List.of("Project ID", task.projectId()),
                    java.util.List.of("Request", task.request()),
                    java.util.List.of("Requester", task.requester()),
                    java.util.List.of("Status", task.status()),
                    java.util.List.of("Created At", task.createdAt()),
                    java.util.List.of("Error", task.error() != null ? task.error() : "none")
                );
                System.out.println(new OutputFormatter(outputFormat).formatTable(headers, rows));

                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to get task: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * List tasks.
     */
    @Command(name = "list", description = "List tasks")
    public static class List implements Callable<Integer> {
        @Option(names = {"--project"}, description = "Filter by project ID")
        private String projectId;

        @Option(names = {"--status"}, description = "Filter by status")
        private String status;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());
            OutputFormatter formatter = new OutputFormatter(outputFormat);

            System.out.println(Ansi.info(" Fetching tasks..."));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.TaskListResponse response = client.listTasks(projectId, status).block();
                if (response == null || response.tasks() == null || response.tasks().isEmpty()) {
                    System.out.println(Ansi.warning(" No tasks found"));
                    return 0;
                }

                java.util.List<String> headers = java.util.List.of("Task ID", "Project", "Status", "Requester", "Created");
                java.util.List<java.util.List<String>> rows = response.tasks().stream()
                    .map(t -> java.util.List.of(t.taskId(), t.projectId(), t.status(), t.requester(), t.createdAt()))
                    .collect(java.util.stream.Collectors.toList());

                System.out.println(new OutputFormatter(outputFormat).formatTable(headers, rows));
                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to list tasks: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * Approve a task plan.
     */
    @Command(name = "approve", description = "Approve a task plan")
    public static class Approve implements Callable<Integer> {
        @Parameters(index = "0", description = "Task ID")
        private String taskId;

        @Option(names = {"--comment"}, description = "Approval comment")
        private String comment;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());

            System.out.println(Ansi.info(" Approving task: " + taskId));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.ApprovalResponse response = client.approveTask(taskId, "cli-user", comment != null ? comment : "").block();
                if (response == null) {
                    System.err.println(Ansi.error(" Failed to approve task"));
                    return 1;
                }

                System.out.println(Ansi.success(" Task approved: " + response.taskId()));
                System.out.println(Ansi.dim("Message: " + response.message()));
                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to approve task: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * Reject a task plan.
     */
    @Command(name = "reject", description = "Reject a task plan")
    public static class Reject implements Callable<Integer> {
        @Parameters(index = "0", description = "Task ID")
        private String taskId;

        @Option(names = {"--reason"}, description = "Rejection reason", required = true)
        private String reason;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());

            System.out.println(Ansi.info(" Rejecting task: " + taskId));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.ApprovalResponse response = client.rejectTask(taskId, "cli-user", reason).block();
                if (response == null) {
                    System.err.println(Ansi.error(" Failed to reject task"));
                    return 1;
                }

                System.out.println(Ansi.warning(" Task rejected: " + response.taskId()));
                System.out.println(Ansi.dim("Reason: " + response.message()));
                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to reject task: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * View task logs.
     */
    @Command(name = "logs", description = "View task logs")
    public static class Logs implements Callable<Integer> {
        @Parameters(index = "0", description = "Task ID")
        private String taskId;

        @Option(names = {"--follow"}, description = "Follow logs in real-time")
        private boolean follow;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());

            System.out.println(Ansi.info(" Fetching logs for task: " + taskId));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.LogsResponse response = client.getLogs(taskId, follow).block();
                if (response == null) {
                    System.err.println(Ansi.error(" Failed to fetch logs"));
                    return 1;
                }

                System.out.println(Ansi.bold("Logs for task: " + taskId));
                System.out.println(response.stdout());
                if (response.stderr() != null && !response.stderr().isBlank()) {
                    System.err.println(Ansi.red("STDERR:"));
                    System.err.println(response.stderr());
                }
                System.out.println(Ansi.dim("Finished: " + response.finished()));
                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to fetch logs: " + e.getMessage()));
                }
                return 1;
            }
        }
    }

    /**
     * View/download task artifacts.
     */
    @Command(name = "artifacts", description = "View/download task artifacts")
    public static class Artifacts implements Callable<Integer> {
        @Parameters(index = "0", description = "Task ID")
        private String taskId;

        @Option(names = {"--download"}, description = "Download all artifacts")
        private boolean download;

        @Option(names = {"--output"}, description = "Output directory for downloaded artifacts")
        private String outputDir;

        @Option(names = {"--view"}, description = "View a specific artifact file")
        private String viewFile;

        @Option(names = {"--open"}, description = "Open report.md in default editor")
        private boolean open;

        @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
        private String outputFormat = "table";

        @Override
        public Integer call() throws Exception {
            OrchestratorClient client = new OrchestratorClient(new ConfigManager().load());
            OutputFormatter formatter = new OutputFormatter(outputFormat);

            System.out.println(Ansi.info(" Fetching artifacts for task: " + taskId));

            try {
                // Check server health first
                OrchestratorClient.HealthResponse health = client.healthCheck().block();
                if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                    return 1;
                }

                OrchestratorClient.ArtifactsResponse response = client.getArtifacts(taskId).block();
                if (response == null || response.artifacts() == null || response.artifacts().isEmpty()) {
                    System.out.println(Ansi.warning(" No artifacts found for task: " + taskId));
                    return 0;
                }

                java.util.List<String> headers = java.util.List.of("Name", "Type", "Size", "Created");
                java.util.List<java.util.List<String>> rows = response.artifacts().stream()
                    .map(a -> java.util.List.of(a.name(), a.type(), formatSize(a.size()), a.createdAt()))
                    .collect(java.util.stream.Collectors.toList());

                System.out.println(formatter.formatTable(headers, rows));

                // Handle view file
                if (viewFile != null && !viewFile.isBlank()) {
                    viewArtifactFile(client, viewFile);
                    return 0;
                }

                // Handle open in editor
                if (open) {
                    openArtifactsInEditor(response);
                    return 0;
                }

                // Handle download
                if (download) {
                    downloadArtifacts(client, response, outputDir);
                    return 0;
                }

                return 0;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("Connection") || msg.contains("connect") || msg.contains("refused") || msg.contains("timeout") || msg.contains("UnknownHost"))) {
                    System.err.println(Ansi.error(" Cannot connect to orchestrator server"));
                    System.err.println(Ansi.dim(" Make sure the server is running at " + new ConfigManager().load().serverUrl()));
                } else {
                    System.err.println(Ansi.error(" Failed to fetch artifacts: " + e.getMessage()));
                }
                return 1;
            }
        }

        private void viewArtifactFile(OrchestratorClient client, String fileName) {
            System.out.println(Ansi.info(" Fetching artifact: " + fileName));
            try {
                byte[] content = client.downloadArtifact(taskId, fileName).block();
                if (content == null) {
                    System.err.println(Ansi.error(" Artifact not found: " + fileName));
                    return;
                }
                System.out.println(new String(content, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println(Ansi.error(" Failed to view artifact: " + e.getMessage()));
            }
        }

        private void openArtifactsInEditor(OrchestratorClient.ArtifactsResponse response) {
            System.out.println(Ansi.warning(" Open in editor not yet implemented for multiple artifacts"));
            System.out.println(Ansi.dim(" Use --view <filename> to view a specific artifact"));
        }

        private void downloadArtifacts(OrchestratorClient client, OrchestratorClient.ArtifactsResponse response, String outputDir) {
            Path outputPath = outputDir != null ? Paths.get(outputDir).toAbsolutePath() : Paths.get("artifacts-" + taskId).toAbsolutePath();
            try {
                Files.createDirectories(outputPath);
                System.out.println(Ansi.info(" Downloading artifacts to: " + outputPath));

                for (OrchestratorClient.Artifact artifact : response.artifacts()) {
                    System.out.print(Ansi.dim(" Downloading " + artifact.name() + "... "));
                    try {
                        byte[] content = client.downloadArtifact(taskId, artifact.name()).block();
                        if (content != null) {
                            Path filePath = outputPath.resolve(artifact.name());
                            Files.write(filePath, content);
                            System.out.println(Ansi.success(" Done (" + formatSize(artifact.size()) + ")"));
                        } else {
                            System.out.println(Ansi.error(" Failed (artifact not found)"));
                        }
                    } catch (Exception e) {
                        System.out.println(Ansi.error(" Failed: " + e.getMessage()));
                    }
                }
                System.out.println(Ansi.success(" Download complete! Artifacts saved to: " + outputPath));
            } catch (Exception e) {
                System.err.println(Ansi.error(" Download failed: " + e.getMessage()));
            }
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}