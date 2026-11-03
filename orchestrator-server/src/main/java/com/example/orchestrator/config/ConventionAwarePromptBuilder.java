package com.example.orchestrator.config;

import com.example.orchestrator.context.ContextBundle;
import com.example.orchestrator.memory.MemoryBundle;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.requirement.RequirementSpec;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConventionAwarePromptBuilder {

    private final ConventionLoader conventionLoader;

    public ConventionAwarePromptBuilder(ConventionLoader conventionLoader) {
        this.conventionLoader = conventionLoader;
    }

    public String buildGenerationPrompt(String spec, Project project, ContextBundle context, MemoryBundle memory) throws IOException {
        ConventionLoader.ProjectConventions conventions = loadConventions(project);
        
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a Spring Boot feature module for: ").append(spec).append("\n\n");
        
        // Add project conventions
        sb.append("FOLLOW THESE CONVENTIONS EXACTLY:\n");
        sb.append("Package structure: ").append(conventions.packageStructure()).append("\n");
        sb.append("Domain: ").append(conventions.domain()).append("\n");
        
        // Get module conventions if available
        String module = inferModule(spec);
        ConventionLoader.ModuleConventions moduleConventions = conventions.getModule(module);
        
        sb.append("Layers: ").append(moduleConventions.layers()).append("\n");
        sb.append("Naming: ").append(moduleConventions.naming()).append("\n");
        sb.append("Patterns:\n");
        moduleConventions.patterns().forEach((key, pattern) -> {
            sb.append("  ").append(key).append(":\n");
            for (String line : pattern.lines().toList()) {
                sb.append("    ").append(line).append("\n");
            }
        });
        
        // Add learned conventions from memory
        String priorityContext = memory.getPriorityContext();
        if (priorityContext != null && !priorityContext.isBlank()) {
            sb.append("\nLEARNED CONVENTIONS FROM THIS PROJECT:\n");
            sb.append(priorityContext).append("\n");
        }
        
        // Add context files
        sb.append("\nRELEVANT CONTEXT FILES:\n");
        for (var file : context.files()) {
            sb.append("=== ").append(file.path()).append(" ===\n");
            sb.append(file.content()).append("\n\n");
        }
        
        sb.append("OUTPUT ONLY the complete file contents for each file.\n");
        sb.append("Files must compile and tests must pass.\n");
        sb.append("Follow the conventions EXACTLY as specified above.\n");
        
        return sb.toString();
    }

    public String buildRequirementPrompt(ContextBundle context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software architect. Convert the natural language request into structured engineering requirements.\n\n");
        sb.append("CONTEXT:\n").append(context.toPromptContext()).append("\n\n");
        sb.append("TASK:\n");
        sb.append("Analyze the user request and the project context. Produce a detailed requirement specification in JSON format.\n\n");
        sb.append("Output ONLY valid JSON matching this exact schema:\n");
        sb.append("{\n");
        sb.append("  \"functionalRequirements\": [\"string\"],\n");
        sb.append("  \"securityRequirements\": [\"string\"],\n");
        sb.append("  \"performanceRequirements\": [\"string\"],\n");
        sb.append("  \"reliabilityRequirements\": [\"string\"],\n");
        sb.append("  \"testingRequirements\": [\"string\"],\n");
        sb.append("  \"observabilityRequirements\": [\"string\"],\n");
        sb.append("  \"configurationRequirements\": [\"string\"],\n");
        sb.append("  \"deploymentRequirements\": [\"string\"],\n");
        sb.append("  \"acceptanceCriteria\": [\"string\"],\n");
        sb.append("  \"assumptions\": [\"string\"],\n");
        sb.append("  \"risks\": [\"string\"]\n");
        sb.append("}\n\n");
        sb.append("RULES:\n");
        sb.append("- Be specific and actionable. No vague statements.\n");
        sb.append("- If a category has no applicable requirements, use an empty array [].\n");
        sb.append("- Mark assumptions explicitly - do not invent requirements silently.\n");
        sb.append("- Identify risks that could impact implementation.\n");
        sb.append("- Acceptance criteria must be testable and measurable.\n");
        sb.append("- No markdown fences, no explanations, no extra text. ONLY the JSON object.\n");
        return sb.toString();
    }

    public String buildPlanningPrompt(RequirementSpec requirements, ContextBundle context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software architect and technical lead. Convert the structured requirements into an ordered implementation plan.\n\n");
        sb.append("CONTEXT:\n").append(context.toPromptContext()).append("\n\n");
        sb.append("REQUIREMENTS:\n");
        sb.append("Functional: ").append(formatList(requirements.functionalRequirements())).append("\n");
        sb.append("Security: ").append(formatList(requirements.securityRequirements())).append("\n");
        sb.append("Performance: ").append(formatList(requirements.performanceRequirements())).append("\n");
        sb.append("Reliability: ").append(formatList(requirements.reliabilityRequirements())).append("\n");
        sb.append("Testing: ").append(formatList(requirements.testingRequirements())).append("\n");
        sb.append("Observability: ").append(formatList(requirements.observabilityRequirements())).append("\n");
        sb.append("Configuration: ").append(formatList(requirements.configurationRequirements())).append("\n");
        sb.append("Deployment: ").append(formatList(requirements.deploymentRequirements())).append("\n");
        sb.append("Acceptance Criteria: ").append(formatList(requirements.acceptanceCriteria())).append("\n");
        sb.append("Assumptions: ").append(formatList(requirements.assumptions())).append("\n");
        sb.append("Risks: ").append(formatList(requirements.risks())).append("\n\n");
        sb.append("TASK:\n");
        sb.append("Create an ordered implementation plan. Each task must be specific, actionable, and independently verifiable.\n\n");
        sb.append("Output ONLY valid JSON matching this exact schema:\n");
        sb.append("{\n");
        sb.append("  \"tasks\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"TASK-001\",\n");
        sb.append("      \"description\": \"string\",\n");
        sb.append("      \"affectedFiles\": [\"string\"],\n");
        sb.append("      \"dependencies\": [\"TASK-XXX\"],\n");
        sb.append("      \"risk\": \"LOW|MEDIUM|HIGH|CRITICAL\",\n");
        sb.append("      \"tools\": [\"listDirectory\", \"readFile\", \"searchFiles\", \"gitStatus\", \"gitDiff\", \"runMavenTest\"],\n");
        sb.append("      \"acceptanceCriteria\": [\"string\"],\n");
        sb.append("      \"verificationSteps\": [\"string\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"summary\": \"string\",\n");
        sb.append("  \"estimatedRounds\": 3\n");
        sb.append("}\n\n");
        sb.append("RULES:\n");
        sb.append("- Order tasks by dependency (dependencies first).\n");
        sb.append("- Risk must be one of: LOW, MEDIUM, HIGH, CRITICAL.\n");
        sb.append("- Tools must be from the SAFE tool list only.\n");
        sb.append("- Each task must have at least one acceptance criterion.\n");
        sb.append("- Estimated rounds: 1-5.\n");
        sb.append("- No markdown fences, no explanations, no extra text. ONLY the JSON object.\n");
        return sb.toString();
    }

    private String inferModule(String spec) {
        String lower = spec.toLowerCase();
        if (lower.contains("gateway") || lower.contains("api") || lower.contains("controller")) {
            return "gateway";
        }
        if (lower.contains("service") || lower.contains("business")) {
            return "service";
        }
        if (lower.contains("data") || lower.contains("repository") || lower.contains("database")) {
            return "repository";
        }
        return "gateway"; // default
    }

    private String formatList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return list.stream()
            .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private ConventionLoader.ProjectConventions loadConventions(Project project) throws IOException {
        // Try to load from project conventions.yaml
        Path conventionsFile = project.rootPath().resolve("conventions.yaml");
        if (Files.exists(project.rootPath().resolve("conventions.yaml"))) {
            return conventionLoader.load(project.rootPath().resolve("conventions.yaml"));
        }
        // Fallback to classpath default
        return conventionLoader.loadFromClasspath();
    }
}