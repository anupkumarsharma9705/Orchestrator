package com.example.orchestrator.cli.interactive;

import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.TablePrinter;
import com.example.orchestrator.cli.util.TerminalSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive plan reviewer - displays plan and prompts for approval.
 */
public class PlanReviewer {

    private final Scanner scanner;
    private final boolean nonInteractive;
    
    // JSON parser with size limits to prevent OOM (H-04, H-07)
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

    public PlanReviewer() {
        this(false);
    }

    public PlanReviewer(boolean nonInteractive) {
        this.scanner = new Scanner(System.in);
        this.nonInteractive = nonInteractive;
    }

    /**
     * Display a plan and prompt for approval.
     */
    public boolean review(OrchestratorClient.TaskResponse plan) {
        // Non-interactive mode: auto-approve with defaults (H-18)
        if (nonInteractive) {
            System.out.println(Ansi.dim("Non-interactive mode: auto-approving plan"));
            return true;
        }
        
        System.out.println();
        System.out.println(Ansi.bold(Ansi.CYAN + TerminalSupport.heading("PLAN REVIEW")));
        System.out.println();

        // Task summary
        System.out.println(Ansi.bold("Task: ") + plan.request());
        System.out.println(Ansi.dim("Task ID: " + plan.taskId()));
        System.out.println(Ansi.dim("Project: " + plan.projectId()));
        System.out.println();

        // Parse and display plan if available
        if (plan.planJson() != null && !plan.planJson().isBlank()) {
            displayPlan(plan.planJson());
        } else {
            System.out.println(Ansi.yellow("  No plan generated yet. Will be created after clarification."));
        }

        System.out.println();
        System.out.println(Ansi.dim("Options:"));
        System.out.println("  " + Ansi.green("1) Approve") + " - Start execution");
        System.out.println("  " + Ansi.yellow("2) Reject") + " - Cancel with reason");
        System.out.println("  " + Ansi.cyan("3) View details") + " - Show full plan JSON");
        System.out.println();

        while (true) {
            System.out.print(Ansi.ARROW + " Choose [1-3]: ");
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1" -> {
                    System.out.println(Ansi.success(" Plan approved. Starting execution..."));
                    return true;
                }
                case "2" -> {
                    System.out.print(Ansi.ARROW + " Rejection reason: ");
                    String reason = scanner.nextLine().trim();
                    if (reason.isBlank()) {
                        System.out.println(Ansi.red(" Reason is required."));
                        continue;
                    }
                    System.out.println(Ansi.warning(" Plan rejected: " + reason));
                    return false;
                }
                case "3" -> {
                    System.out.println(Ansi.dim("Full plan JSON:"));
                    System.out.println(plan.planJson());
                    System.out.println();
                }
                default -> System.out.println(Ansi.red(" Invalid option. Enter 1, 2, or 3."));
            }
        }
    }

    /**
     * Display plan in a readable format.
     */
    private void displayPlan(String planJson) {
        // Check input size before parsing (H-04: OOM prevention)
        if (planJson.length() > 1_000_000) { // 1MB limit
            System.out.println(Ansi.yellow("  Plan JSON too large (" + planJson.length() + " chars), skipping display"));
            return;
        }

        try {
            // Try to parse as simple JSON and display tasks using secure mapper
            Map<String, Object> plan = SECURE_MAPPER.readValue(planJson, Map.class);

            List<Map<String, Object>> tasks = (List<Map<String, Object>>) plan.get("tasks");
            if (tasks != null && !tasks.isEmpty()) {
                System.out.println(Ansi.bold("Tasks:"));
                System.out.println();

                List<String> headers = List.of("#", "ID", "Title", "Files", "Depends On");
                List<List<String>> rows = new java.util.ArrayList<>();

                for (int i = 0; i < tasks.size(); i++) {
                    Map<String, Object> task = tasks.get(i);
                    String id = safeString(task.get("id"));
                    String title = safeString(task.get("title"));
                    List<String> files = (List<String>) task.get("files");
                    String filesStr = files != null ? String.join(", ", files) : "";
                    List<String> deps = (List<String>) task.get("dependsOn");
                    String depsStr = deps != null ? String.join(", ", deps) : "";

                    rows.add(List.of(
                        String.valueOf(i + 1),
                        id,
                        truncate(title, 40),
                        truncate(filesStr, 30),
                        truncate(depsStr, 20)
                    ));
                }

                TablePrinter.printTable(headers, rows);
                System.out.println();
            }

            // Show acceptance criteria if present
            Object acceptance = plan.get("acceptanceCriteria");
            if (acceptance instanceof List<?> criteria && !criteria.isEmpty()) {
                System.out.println(Ansi.bold("Acceptance Criteria:"));
                for (Object c : criteria) {
                    System.out.println("  " + Ansi.BULLET + " " + c);
                }
                System.out.println();
            }

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            System.out.println(Ansi.yellow("  Could not parse plan JSON (invalid JSON): " + e.getMessage()));
        } catch (com.fasterxml.jackson.databind.exc.InvalidFormatException e) {
            System.out.println(Ansi.yellow("  Could not parse plan JSON (format error): " + e.getMessage()));
        } catch (Exception e) {
            System.out.println(Ansi.yellow("  Could not parse plan JSON: " + e.getMessage()));
        }
    }

    private String safeString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}