package com.example.orchestrator.core.planner;

import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ChatRequest.Message;
import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.RequirementsDoc;
import com.example.orchestrator.model.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Planner: Converts RequirementsDoc + RefinedPrompt into an ordered TaskPlan.
 * 
 * Flow:
 * 1. Takes RequirementsDoc (from Clarifier) and RefinedPrompt (from Refiner)
 * 2. Sends to PLANNER model with planning prompt
 * 3. Model returns tasks as JSON (id, title, description, files[], dependsOn[], reqIds[], acceptance[])
 * 4. Validates: no cycles, all req IDs covered, relative paths
 * 5. Topological sort for task order
 * 6. Returns Plan with traceability table
 */
@Service
public class Planner {

    private static final Logger log = LoggerFactory.getLogger(Planner.class);

    private final ModelRouter modelRouter;
    private final PlannerConfig config;

    public Planner(ModelRouter modelRouter, PlannerConfig config) {
        this.modelRouter = modelRouter;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("Planner initialized");
    }

    /**
     * Creates a Plan from RequirementsDoc and RefinedPrompt.
     * 
     * @param requirementsDoc The clarified requirements
     * @param refinedPrompt The structured build prompt
     * @return Plan with ordered tasks
     */
    public Mono<Plan> createPlan(RequirementsDoc requirementsDoc, String refinedPrompt) {
        log.info("Creating plan for project: {}", requirementsDoc.projectName());
        
        String prompt = buildPlanningPrompt(requirementsDoc, refinedPrompt);
        Map<String, Object> metadata = Map.of(
            "taskId", "plan-" + System.currentTimeMillis(),
            "profile", "planner",
            "projectName", requirementsDoc.projectName()
        );

        ChatRequest request = new ChatRequest(
            List.of(new Message("user", prompt, null, List.of())),
            "planner-model",
            0.1, 4096, 0.9,
            config.getSystemPrompt(),
            List.of(),
            metadata
        );

        return modelRouter.chatWithProfile(request, "planner")
            .map(response -> parseTasks(response, requirementsDoc))
            .map(this::validateAndSort)
            .doOnNext(plan -> log.info("Generated plan with {} tasks for project {}", 
                plan.tasks().size(), requirementsDoc.projectName()));
    }

    /**
     * Builds the planning prompt with RequirementsDoc and RefinedPrompt context.
     */
    private String buildPlanningPrompt(RequirementsDoc doc, String refinedPrompt) {
        String requirementsSummary = doc.requirements().stream()
            .map(r -> "- " + r.id() + " [" + r.category() + "] " + r.text() + " (Priority: " + r.priority() + ")")
            .reduce("", (a, b) -> a + "\n" + b);

        String clarificationsSummary = doc.clarifications().stream()
            .filter(Clarification::isAnswered)
            .map(c -> "- " + c.id() + ": " + c.question() + " = " + c.getEffectiveAnswer())
            .reduce("", (a, b) -> a + "\n" + b);

        return """
            You are an expert software architect and project planner. Create a detailed implementation plan
            for a Spring Boot REST API based on the clarified requirements.

            PROJECT: %s
            VERSION: %s

            REFINED PROMPT:
            %s

            REQUIREMENTS:
            %s

            CLARIFICATIONS:
            %s

            ASSUMPTIONS:
            %s

            OUTPUT FORMAT (JSON array only, no markdown):
            [
              {
                "id": "T-001",
                "title": "Create Book entity",
                "description": "Create JPA entity for Book with title, author, genre, isbn fields",
                "files": ["src/main/java/com/example/library/domain/Book.java"],
                "dependsOn": [],
                "requirementIds": ["R-001"],
                "acceptance": [
                  "Entity compiles",
                  "Fields match requirements",
                  "JPA annotations present"
                ]
              }
            ]

            RULES:
            - Return 5-15 tasks in dependency order
            - Each task MUST have: id (T-XXX), title, description, files[], dependsOn[], requirementIds[], acceptance[]
            - File paths MUST be relative (no absolute paths)
            - dependsOn[] references other task IDs
            - requirementIds[] references requirement IDs from above
            - acceptance[] are verifiable criteria
            - Typical order: Entities -> Repositories -> Services -> Controllers -> Security -> Tests -> Docker -> README
            - NO cycles in dependencies
            - ALL requirement IDs must be covered by at least one task
            """.formatted(
                doc.projectName(), doc.version(), refinedPrompt,
                requirementsSummary, clarificationsSummary,
                String.join("\n", doc.assumptions().stream().map(a -> "- " + a).toList())
            );
    }

    /**
     * Parses model response into PlanTask objects.
     */
    private Plan parseTasks(ChatResponse response, RequirementsDoc requirementsDoc) {
        String content = response.content();
        List<PlanTask> tasks = new ArrayList<>();

        try {
            String json = extractJsonArray(content);
            if (json != null && !json.trim().isEmpty() && !json.trim().equals("[]")) {
                tasks = parseJsonToTasks(json);
            }
        } catch (Exception e) {
            log.warn("Failed to parse tasks from model response: {}", e.getMessage());
            tasks = generateFallbackTasks(requirementsDoc);
        }

        // Fallback if parsing returned empty
        if (tasks.isEmpty()) {
            log.warn("Parsing returned empty tasks, using fallback");
            tasks = generateFallbackTasks(requirementsDoc);
        }

        // Ensure sequential IDs
        for (int i = 0; i < tasks.size(); i++) {
            PlanTask t = tasks.get(i);
            String expectedId = "T-%03d".formatted(i + 1);
            if (!t.id().equals(expectedId)) {
                tasks.set(i, new PlanTask(
                    expectedId, t.title(), t.description(), t.files(), t.dependsOn(),
                    t.requirementIds(), t.acceptance(), t.status(), t.createdAt(), t.updatedAt()
                ));
            }
        }

        return new Plan(requirementsDoc.projectName(), "v1", tasks, Instant.now(), Instant.now());
    }

    /**
     * Validates plan and returns topologically sorted tasks.
     */
    private Plan validateAndSort(Plan plan) {
        Plan.ValidationResult validation = plan.validate();
        if (!validation.valid()) {
            log.warn("Plan validation failed: {}", validation.errors());
            // Try fallback
            return generateFallbackPlan(plan.projectName());
        }

        // Return plan with topologically sorted tasks
        List<PlanTask> sorted = plan.getTopologicalOrder();
        return new Plan(plan.projectName(), plan.version(), sorted, plan.createdAt(), Instant.now());
    }

    /**
     * Extracts JSON array from model response.
     */
    private String extractJsonArray(String content) {
        String trimmed = content.trim();
        // If content is already a JSON array, use it directly
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed;
        }
        // Otherwise try to find JSON array within the content
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*\\}\\s*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return trimmed;
    }

    /**
     * Parses JSON array to PlanTask list.
     */
    private List<PlanTask> parseJsonToTasks(String json) {
        List<PlanTask> result = new ArrayList<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        
        List<String> objects = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : trimmed.toCharArray()) {
            current.append(c);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    objects.add(current.toString());
                    current = new StringBuilder();
                }
            }
        }

        for (String obj : objects) {
            try {
                PlanTask t = parseTask(obj);
                if (t != null) result.add(t);
            } catch (Exception e) {
                log.debug("Failed to parse task: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Parses a single task JSON object.
     */
    private PlanTask parseTask(String json) {
        String id = extractValue(json, "id");
        String title = extractValue(json, "title");
        String description = extractValue(json, "description");
        List<String> files = extractStringList(json, "files");
        List<String> dependsOn = extractStringList(json, "dependsOn");
        List<String> requirementIds = extractStringList(json, "requirementIds");
        List<String> acceptance = extractStringList(json, "acceptance");

        if (id == null || title == null || description == null) return null;

        Instant now = Instant.now();
        return new PlanTask(
            id, title, description, files, dependsOn, requirementIds, acceptance,
            com.example.orchestrator.model.TaskStatus.PENDING, now, now
        );
    }

    private String extractValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> extractStringList(String json, String key) {
        List<String> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String listStr = matcher.group(1);
            for (String item : listStr.split(",")) {
                String cleaned = item.trim().replaceAll("^\"|\"$", "");
                if (!cleaned.isEmpty()) result.add(cleaned);
            }
        }
        return result;
    }

    /**
     * Generates sensible fallback tasks if model fails.
     */
    private List<PlanTask> generateFallbackTasks(RequirementsDoc doc) {
        Instant now = Instant.now();
        return List.of(
            new PlanTask("T-001", "Create Book entity", "Create JPA entity for Book",
                List.of("src/main/java/com/example/library/domain/Book.java"),
                List.of(), List.of("R-001"),
                List.of("Entity compiles", "Fields match requirements", "JPA annotations present"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-002", "Create Book repository", "Create Spring Data JPA repository",
                List.of("src/main/java/com/example/library/repository/BookRepository.java"),
                List.of("T-001"), List.of("R-001"),
                List.of("Repository compiles", "Extends JpaRepository"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-003", "Create Book service", "Create service with CRUD operations",
                List.of("src/main/java/com/example/library/service/BookService.java"),
                List.of("T-002"), List.of("R-002"),
                List.of("Service compiles", "CRUD methods implemented", "@Transactional present"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-004", "Create Book controller", "Create REST controller with endpoints",
                List.of("src/main/java/com/example/library/controller/BookController.java"),
                List.of("T-003"), List.of("R-002"),
                List.of("Controller compiles", "GET/POST/PUT/DELETE endpoints", "@Valid on DTOs"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-005", "Create unit tests", "Create unit tests for service layer",
                List.of("src/test/java/com/example/library/service/BookServiceTest.java"),
                List.of("T-003"), List.of("R-003"),
                List.of("Tests compile", "Coverage > 80%"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now)
        );
    }

    /**
     * Generates a complete fallback plan.
     */
    private Plan generateFallbackPlan(String projectName) {
        Instant now = Instant.now();
        List<PlanTask> tasks = List.of(
            new PlanTask("T-001", "Create entities", "Create JPA entities",
                List.of("src/main/java/com/example/library/domain/Book.java"),
                List.of(), List.of("R-001"),
                List.of("Entities compile", "JPA annotations present"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-002", "Create repositories", "Create Spring Data repositories",
                List.of("src/main/java/com/example/library/repository/BookRepository.java"),
                List.of("T-001"), List.of("R-001"),
                List.of("Repositories compile", "Extend JpaRepository"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-003", "Create services", "Create service layer",
                List.of("src/main/java/com/example/library/service/BookService.java"),
                List.of("T-002"), List.of("R-002"),
                List.of("Services compile", "CRUD implemented", "@Transactional"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-004", "Create controllers", "Create REST controllers",
                List.of("src/main/java/com/example/library/controller/BookController.java"),
                List.of("T-003"), List.of("R-002"),
                List.of("Controllers compile", "Endpoints present", "@Valid on DTOs"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now),
            new PlanTask("T-005", "Create tests", "Create unit and integration tests",
                List.of("src/test/java/com/example/library/service/BookServiceTest.java"),
                List.of("T-003", "T-004"), List.of("R-003"),
                List.of("Tests compile", "Coverage > 80%"),
                com.example.orchestrator.model.TaskStatus.PENDING, now, now)
        );
        return new Plan(projectName, "v1", tasks, now, now);
    }
}