package com.example.orchestrator.core.fixer;

import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.core.coder.FileTools;
import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.verify.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Proposes fixes for findings by calling the LLM with context.
 * Uses the REVIEWER profile for fix suggestions.
 */
@Component
public class Fixer {

    private static final Logger log = LoggerFactory.getLogger(Fixer.class);

    private final ModelRouter modelRouter;
    private final FileTools fileTools;
    private final FixerConfig config;

    public Fixer(ModelRouter modelRouter, FileTools fileTools, FixerConfig config) {
        this.modelRouter = modelRouter;
        this.fileTools = fileTools;
        this.config = config;
    }

    /**
     * Proposes fixes for a set of findings in a specific file.
     * 
     * @param workspacePath Project workspace root
     * @param filePath Relative path to the file to fix
     * @param findings List of findings in this file
     * @param roundNumber Current fix round number
     * @return Mono emitting list of proposed fixes
     */
    public Mono<List<Fix>> proposeFixes(Path workspacePath, String filePath, List<Finding> findings, int roundNumber) {
        log.info("Proposing fixes for {} findings in {} (round {})", findings.size(), filePath, roundNumber);

        // Read current file content from workspace (not staging)
        String currentContent = readWorkspaceFile(workspacePath, filePath);
        if (currentContent == null || currentContent.isBlank()) {
            log.warn("File {} is empty or doesn't exist, skipping", filePath);
            return Mono.just(List.of());
        }

        // Build fix prompt
        String prompt = buildFixPrompt(filePath, currentContent, findings, roundNumber);

        // Call LLM with REVIEWER profile (low temp for deterministic fixes)
        return callLlmForFix(prompt)
            .map(response -> parseFixes(response, filePath, findings))
            .doOnNext(fixes -> log.info("Proposed {} fixes for {}", fixes.size(), filePath));
    }

    private String readWorkspaceFile(Path workspacePath, String relativePath) {
        try {
            return java.nio.file.Files.readString(workspacePath.resolve(relativePath));
        } catch (Exception e) {
            log.warn("Failed to read workspace file {}: {}", relativePath, e.getMessage());
            return null;
        }
    }

    private Mono<String> callLlmForFix(String prompt) {
        ChatRequest request = new ChatRequest(
            List.of(new ChatRequest.Message("user", prompt, null, List.of())),
            "reviewer-model",
            0.1,
            4096,
            0.9,
            "You are a senior Java developer fixing code issues. Make minimal changes.",
            List.of(),
            Map.of("task", "fix", "profile", "reviewer")
        );

        return modelRouter.chatWithProfile(request, "reviewer")
            .map(ChatResponse::content);
    }

    private String buildFixPrompt(String filePath, String currentContent, List<Finding> findings, int roundNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior Java developer fixing code issues.\n\n");
        sb.append("FILE: ").append(filePath).append("\n");
        sb.append("ROUND: ").append(roundNumber).append("\n\n");
        sb.append("CURRENT CODE:\n```java\n").append(currentContent).append("\n```\n\n");
        sb.append("ISSUES TO FIX:\n");

        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append(i + 1).append(". [").append(f.severity()).append("] ")
              .append(f.ruleId()).append(": ").append(f.message());
            if (f.lineNumber() > 0) {
                sb.append(" (line ").append(f.lineNumber()).append(")");
            }
            if (f.codeSnippet() != null && !f.codeSnippet().isBlank()) {
                sb.append("\n   Code: ").append(f.codeSnippet());
            }
            sb.append("\n");
        }

        sb.append("\nRULES:\n");
        sb.append("1. Make MINIMAL changes - only fix the reported issues\n");
        sb.append("2. Do NOT delete tests to make them pass\n");
        sb.append("3. Preserve existing functionality\n");
        sb.append("4. Follow Spring Boot best practices\n");
        sb.append("5. Return ONLY the fixed file content in a code block\n\n");
        sb.append("RESPONSE FORMAT:\n```java\n<FIXED FILE CONTENT>\n```");

        return sb.toString();
    }

    private List<Fix> parseFixes(String response, String filePath, List<Finding> findings) {
        // Extract code block from response
        String fixedContent = extractCodeBlock(response);
        if (fixedContent == null) {
            log.warn("No code block found in fix response for {}", filePath);
            return List.of();
        }

        // Return a single fix with the entire file content
        return List.of(new Fix(
            "fix-" + System.nanoTime(),
            filePath,
            findings.stream().map(Finding::fingerprint).toList(),
            fixedContent,
            "LLM-proposed fix for " + findings.size() + " findings"
        ));
    }

    private String extractCodeBlock(String response) {
        // Find ```java ... ``` block
        int start = response.indexOf("```java");
        if (start == -1) {
            start = response.indexOf("```");
            if (start == -1) return null;
        }
        start = response.indexOf('\n', start) + 1;
        int end = response.indexOf("```", start);
        if (end == -1) return null;
        return response.substring(start, end).trim();
    }
}