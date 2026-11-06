package com.example.orchestrator.cli.interactive;

import com.example.orchestrator.cli.client.OrchestratorClient;
import com.example.orchestrator.cli.util.Ansi;
import com.example.orchestrator.cli.util.Spinner;
import com.example.orchestrator.cli.util.TablePrinter;
import com.example.orchestrator.cli.util.TerminalSupport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders live progress during task execution.
 * Shows spinner, task table, and elapsed time.
 */
public class ProgressRenderer {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread renderThread;
    private Instant startTime;
    private final Map<String, TaskProgress> taskProgress = new ConcurrentHashMap<>();
    private String currentPhase = "";
    private String currentMessage = "";

    /**
     * Start rendering progress.
     */
    public void start(String initialMessage) {
        this.currentMessage = initialMessage;
        this.startTime = Instant.now();
        this.running.set(true);

        renderThread = new Thread(this::renderLoop);
        renderThread.setDaemon(true);
        renderThread.start();
    }

    /**
     * Update current phase/message.
     */
    public void updatePhase(String phase, String message) {
        this.currentPhase = phase;
        this.currentMessage = message;
    }

    /**
     * Update task progress.
     */
    public void updateTask(String taskId, String status, String message) {
        TaskProgress progress = taskProgress.computeIfAbsent(taskId, k -> new TaskProgress());
        progress.status = status;
        progress.message = message;
        progress.lastUpdate = Instant.now();
    }

    /**
     * Mark task as complete.
     */
    public void completeTask(String taskId, boolean success, String message) {
        TaskProgress progress = taskProgress.computeIfAbsent(taskId, k -> new TaskProgress());
        progress.status = success ? "COMPLETED" : "FAILED";
        progress.message = message;
        progress.completed = true;
    }

    /**
     * Stop rendering and show final status.
     */
    public void stop(boolean success, String finalMessage) {
        running.set(false);
        if (renderThread != null) {
            renderThread.interrupt();
            try {
                renderThread.join(200);
            } catch (InterruptedException ignored) {}
        }

        // Clear line and show final status
        System.out.print("\r" + Ansi.CLEAR_LINE);
        String icon = success ? Ansi.SUCCESS : Ansi.ERROR;
        String elapsed = formatElapsed(Duration.between(startTime, Instant.now()));
        System.out.println(icon + " " + finalMessage + " " + Ansi.dim(elapsed));
    }

    private void renderLoop() {
        // H-11: Create spinner once, update message instead of recreating
        Spinner spinner = new Spinner(currentMessage);
        spinner.start();

        while (running.get()) {
            try {
                Thread.sleep(200);
                if (!running.get()) break;

                // Update spinner message with phase (H-11: reuse spinner, don't recreate)
                String displayMsg = currentPhase.isEmpty() ? currentMessage : currentPhase + ": " + currentMessage;
                spinner.updateMessage(displayMsg);

                // Render task table every 2 seconds
                if (!taskProgress.isEmpty()) {
                    System.out.println(); // New line after spinner
                    renderTaskTable();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {}
        }
        spinner.stop(false, "");
    }

    private void renderTaskTable() {
        if (taskProgress.isEmpty()) return;

        List<String> headers = List.of("Task", "Status", "Message");
        List<List<String>> rows = taskProgress.entrySet().stream()
            .map(e -> List.of(
                e.getKey(),
                formatStatus(e.getValue().status),
                truncate(e.getValue().message, 50)
            ))
            .collect(java.util.stream.Collectors.toList());

        System.out.println(Ansi.dim("Tasks:"));
        TablePrinter.printTable(List.of("TASK", "STATUS", "MESSAGE"), rows);
        System.out.println();
    }

    private String formatStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> Ansi.success(TerminalSupport.CHECK + " DONE");
            case "FAILED" -> Ansi.error(TerminalSupport.CROSS + " FAIL");
            case "RUNNING" -> Ansi.info(TerminalSupport.RUNNING + " RUN");
            case "PENDING" -> Ansi.dim(TerminalSupport.PENDING + " WAIT");
            default -> status;
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String formatElapsed(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        return minutes + "m " + (seconds % 60) + "s";
    }

    private static class TaskProgress {
        String status = "PENDING";
        String message = "";
        Instant lastUpdate = Instant.now();
        boolean completed = false;
    }
}