package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.interactive.ClarifierPrompter;
import com.example.orchestrator.cli.interactive.PlanReviewer;
import com.example.orchestrator.cli.interactive.ProgressRenderer;
import com.example.orchestrator.cli.offline.OfflineModeService;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
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
 * Generate a new Spring Boot project from a natural language description.
 */
@Command(
    name = "generate",
    description = "Generate a new Spring Boot project from a natural language description",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Project name (required)", required = true)
    private String name;

    @Option(names = {"-d", "--description"}, description = "Natural language description of the project", required = true)
    private String description;

    @Option(names = {"--project-root"}, description = "Root directory for the project (default: current directory)")
    private String projectRoot;

    @Option(names = {"--non-interactive"}, description = "Run in non-interactive mode (use defaults, auto-approve)")
    private boolean nonInteractive;

    @Option(names = {"--dry-run"}, description = "Show what would be generated without creating files")
    private boolean dryRun;

    @Option(names = {"--offline"}, description = "Generate project locally without server (no server required)")
    private boolean offline;

    @Option(names = {"--base-package"}, description = "Base Java package (e.g., com.example.library)")
    private String basePackage;

    @Option(names = {"--output-format"}, description = "Output format: table, json, markdown")
    private String outputFormat = "table";

    @Option(names = {"--timeout"}, description = "Timeout in seconds (default: 600)")
    private int timeoutSeconds = 600;

    @ParentCommand
    private CompanionCli parent;

    // JSON parser with size limits to prevent OOM (H-04, H-07)
    // 1MB max document size, 100KB max string length
    private static final ObjectMapper SECURE_MAPPER = createSecureMapper();
    
    private static ObjectMapper createSecureMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Enable strict parsing
        mapper.configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
        // Size limits (H-07) - Jackson 2.15+ uses StreamReadConstraints
        mapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxDocumentLength(1_000_000) // 1MB max document
                .maxNestingDepth(100)
                .maxNumberLength(100)
                .maxStringLength(100_000) // 100KB max string
                .build()
        );
        return mapper;
    }

    @Override
    public Integer call() throws Exception {
        // Validate project root
        Path root = resolveProjectRoot();
        if (!Files.exists(root)) {
            System.err.println(Ansi.error(" Project root does not exist: " + root));
            return 1;
        }
        if (!Files.isDirectory(root)) {
            System.err.println(Ansi.error(" Project root is not a directory: " + root));
            return 1;
        }

        // Load config
        CliConfig config = new ConfigManager().load();

        // Initialize components
        OrchestratorClient client = new OrchestratorClient(config);
        OutputFormatter formatter = new OutputFormatter(outputFormat);
        ClarifierPrompter prompter = new ClarifierPrompter(nonInteractive);
        PlanReviewer planReviewer = new PlanReviewer(nonInteractive);
        ProgressRenderer progress = new ProgressRenderer();

        System.out.println(Ansi.bold(Ansi.CYAN + "🚀 Generating project: " + name));
        System.out.println(Ansi.dim("Description: " + description));
        System.out.println(Ansi.dim("Project root: " + root.toAbsolutePath()));
        if (offline) {
            System.out.println(Ansi.dim("Mode: OFFLINE (no server required)"));
        }
        System.out.println();

        // Offline mode - generate locally without server
        if (offline) {
            return runOfflineGeneration(config, formatter, root);
        }

        // Step 1: Health check
        System.out.println(Ansi.info(" Checking server health..."));
        try {
            OrchestratorClient.HealthResponse health = client.healthCheck().block();
            if (health == null || !"UP".equalsIgnoreCase(health.status())) {
                System.err.println(Ansi.error(" Server is not healthy: " + health));
                return 1;
            }
            System.out.println(Ansi.success(" Server is healthy"));
        } catch (Exception e) {
            System.err.println(Ansi.error(" Cannot connect to server: " + e.getMessage()));
            System.err.println(Ansi.dim(" Make sure the orchestrator server is running at " + config.serverUrl()));
            System.err.println(Ansi.dim(" Or use --offline flag to generate locally without server"));
            return 1;
        }

        // Step 2: Submit initial task to start clarification
        System.out.println(Ansi.info(" Submitting task for clarification..."));
        OrchestratorClient.CreateTaskRequest taskRequest = new OrchestratorClient.CreateTaskRequest(
            name, // projectId
            description, // request
            config.defaultProject().isEmpty() ? "cli-user" : config.defaultProject() // requester
        );

        OrchestratorClient.TaskResponse task = client.submitTask(taskRequest).block();
        if (task == null) {
            System.err.println(Ansi.error(" Failed to submit task"));
            return 1;
        }

        System.out.println(Ansi.success(" Task created: " + task.taskId()));
        System.out.println();

        // Step 3: Clarification loop - poll for questions, prompt user, submit answers
        OrchestratorClient.TaskResponse clarifiedTask = runClarificationLoop(client, task.taskId(), progress, prompter, nonInteractive);

        // Step 4: Plan review & approval
        if (!planReviewer.review(clarifiedTask)) {
            System.out.println(Ansi.warning(" Generation cancelled by user"));
            return 0;
        }

        // Step 5: Execute generation with progress
        progress.start("Starting generation...");
        OrchestratorClient.TaskResponse finalTask = pollForCompletion(client, clarifiedTask.taskId(), progress);

        // Step 6: Display results
        displayResults(client, finalTask, formatter);

        return 0;
    }

    private Integer runOfflineGeneration(CliConfig config, OutputFormatter formatter, Path root) throws Exception {
        // Initialize offline mode service (lazy init to avoid server dependency)
        OfflineModeService offlineModeService = new OfflineModeService(
            new com.example.orchestrator.templates.TemplateEngine()
        );

        System.out.println(Ansi.info(" Generating project offline (no server required)..."));
        System.out.println();

        OfflineModeService.OfflineGenerationResult result =
            offlineModeService.generateOffline(name, description, root, basePackage, dryRun);

        if (result.success()) {
            System.out.println();
            System.out.println(Ansi.success(" ✓ " + result.message()));
            System.out.println();
            System.out.println(Ansi.bold("Files that would be generated:"));
            for (String file : result.files()) {
                System.out.println(Ansi.dim("  " + file));
            }
            System.out.println();
            System.out.println(Ansi.dim("Plan:"));
            System.out.println(Ansi.dim("  Tasks: " + result.plan().tasks().size()));
            for (com.example.orchestrator.model.PlanTask task : result.plan().tasks()) {
                System.out.println(Ansi.dim("  - " + task.title() + " (" + task.files().size() + " files)"));
            }

            if (dryRun) {
                System.out.println();
                System.out.println(Ansi.warning(" DRY RUN: No files were created. Run without --dry-run to generate."));
            } else {
                System.out.println();
                System.out.println(Ansi.success(" ✓ Project generated at: " + result.projectPath()));
            }
            return 0;
        } else {
            System.err.println(Ansi.error(" Offline generation failed: " + result.message()));
            return 1;
        }
    }

    private OrchestratorClient.TaskResponse runClarificationLoop(OrchestratorClient client, String taskId, ProgressRenderer progress, ClarifierPrompter prompter, boolean nonInteractive) {
        progress.updatePhase("Clarification", "Waiting for clarifying questions...");

        while (true) {
            OrchestratorClient.TaskResponse task = client.getTask(taskId).block();
            if (task == null) throw new RuntimeException("Task not found");

            if ("WAITING_CLARIFICATION".equals(task.status())) {
                // Parse questions from task.planJson()
                List<ClarificationQuestion> questions = parseClarificationQuestions(task.planJson());
                if (questions.isEmpty()) {
                    // No questions yet, wait and retry
                    progress.updatePhase("Clarification", "Waiting for questions...");
                    try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                    continue;
                }

                // Prompt user for each question
                Map<String, String> answers = new java.util.LinkedHashMap<>();
                for (ClarificationQuestion q : questions) {
                    String answer = prompter.prompt(
                        q.text(),
                        q.defaultValue(),
                        q.options(),
                        q.helpText()
                    );
                    answers.put(q.id(), answer);
                }

                // Submit answers
                progress.updatePhase("Clarification", "Submitting answers...");
                task = client.submitClarificationAnswers(taskId, answers).block();
                if (task == null) throw new RuntimeException("Failed to submit clarification answers");

                System.out.println(Ansi.success(" Answers submitted"));
            } else if ("WAITING_APPROVAL".equals(task.status())) {
                return task;
            } else if ("COMPLETED".equals(task.status()) || "FAILED".equals(task.status())) {
                return task;
            } else {
                // Wait for next poll
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }
        }
    }

    private List<ClarificationQuestion> parseClarificationQuestions(String planJson) {
        List<ClarificationQuestion> questions = new java.util.ArrayList<>();
        if (planJson == null || planJson.isBlank()) return questions;

        // Check input size before parsing (H-04: OOM prevention)
        if (planJson.length() > 1_000_000) { // 1MB limit
            System.err.println(Ansi.warning(" Plan JSON too large (" + planJson.length() + " chars), skipping clarification parsing"));
            return questions;
        }

        try {
            // Use secure mapper with size limits
            Map<String, Object> data = SECURE_MAPPER.readValue(planJson, Map.class);

            // Check for questions array
            Object questionsObj = data.get("questions");
            if (questionsObj instanceof List<?> list) {
                for (Object q : list) {
                    if (q instanceof Map<?, ?> qMap) {
                        String id = safeString(qMap.get("id"));
                        String text = safeString(qMap.get("text"));
                        String defaultValue = safeString(qMap.get("defaultValue"));
                        String helpText = safeString(qMap.get("helpText"));

                        List<String> options = List.of();
                        Object optionsObj = qMap.get("options");
                        if (optionsObj instanceof List<?> optList) {
                            options = optList.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
                        }

                        if (!id.isBlank() && !text.isBlank()) {
                            questions.add(new ClarificationQuestion(id, text, defaultValue, options, helpText));
                        }
                    }
                }
            }

            // Fallback: check for old format with "clarificationQuestions"
            if (questions.isEmpty()) {
                Object clarifObj = data.get("clarificationQuestions");
                if (clarifObj instanceof List<?> list) {
                    for (Object q : list) {
                        if (q instanceof Map<?, ?> qMap) {
                            String id = safeString(qMap.get("id"));
                            String text = safeString(qMap.get("text"));
                            String defaultValue = safeString(qMap.get("defaultValue"));
                            String helpText = safeString(qMap.get("helpText"));

                            List<String> options = List.of();
                            Object optionsObj = qMap.get("options");
                            if (optionsObj instanceof List<?> optList) {
                                options = optList.stream().map(Object::toString).collect(java.util.stream.Collectors.toList());
                            }

                            if (!id.isBlank() && !text.isBlank()) {
                                questions.add(new ClarificationQuestion(id, text, defaultValue, options, helpText));
                            }
                        }
                    }
                }
            }

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            System.err.println(Ansi.warning(" Could not parse clarification questions (invalid JSON): " + e.getMessage()));
        } catch (com.fasterxml.jackson.databind.exc.InvalidFormatException e) {
            System.err.println(Ansi.warning(" Could not parse clarification questions (format error): " + e.getMessage()));
        } catch (Exception e) {
            System.err.println(Ansi.warning(" Could not parse clarification questions: " + e.getMessage()));
        }

        return questions;
    }

    private String safeString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private record ClarificationQuestion(String id, String text, String defaultValue, List<String> options, String helpText) {}

    private OrchestratorClient.TaskResponse pollForCompletion(OrchestratorClient client, String taskId, ProgressRenderer progress) {
        progress.updatePhase("Generation", "Executing tasks...");

        while (true) {
            OrchestratorClient.TaskResponse task = client.getTask(taskId).block();
            if (task == null) throw new RuntimeException("Task not found");

            // Update progress with task status
            progress.updateTask(task.taskId(), task.status(), task.error() != null ? task.error() : "");

            if ("COMPLETED".equals(task.status())) {
                progress.stop(true, "Generation completed successfully!");
                return task;
            }

            if ("FAILED".equals(task.status())) {
                progress.stop(false, "Generation failed: " + task.error());
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
        System.out.println(Ansi.bold(Ansi.CYAN + "═══ GENERATION COMPLETE ═══"));
        System.out.println();

        if ("COMPLETED".equals(task.status())) {
            System.out.println(Ansi.success(" Project generated successfully!"));
            System.out.println(Ansi.dim("Task ID: " + task.taskId()));
            System.out.println();

            // Show artifacts if available
            try {
                var artifacts = client.getArtifacts(task.taskId()).block();
                if (artifacts != null && artifacts.artifacts() != null && !artifacts.artifacts().isEmpty()) {
                    System.out.println(Ansi.bold("Artifacts:"));
                    List<String> headers = List.of("Name", "Type", "Size", "Created");
                    List<List<String>> rows = artifacts.artifacts().stream()
                        .map(a -> List.of(a.name(), a.type(), formatSize(a.size()), a.createdAt()))
                        .collect(java.util.stream.Collectors.toList());
                    System.out.println(new OutputFormatter(outputFormat).formatTable(headers, rows));
                }
            } catch (Exception e) {
                System.err.println(Ansi.warning(" Could not fetch artifacts: " + e.getMessage()));
            }
        } else {
            System.out.println(Ansi.error(" Generation failed"));
            if (task.error() != null) {
                System.out.println(Ansi.dim("Error: " + task.error()));
            }
        }
    }

    private Path resolveProjectRoot() {
        if (projectRoot != null && !projectRoot.isBlank()) {
            return Paths.get(projectRoot).toAbsolutePath().normalize();
        }
        return Paths.get(".").toAbsolutePath().normalize();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}