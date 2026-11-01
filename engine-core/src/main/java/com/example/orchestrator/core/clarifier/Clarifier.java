package com.example.orchestrator.core.clarifier;

import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ChatRequest.Message;
import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.ClarificationType;
import com.example.orchestrator.model.Requirement;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clarifier: Converts a vague natural language request into a complete RequirementsDoc.
 * 
 * Flow:
 * 1. User provides initial request (e.g., "REST CRUD for books with login")
 * 2. Clarifier sends request to PLANNER model with checklist prompt
 * 3. Model returns ≤8 questions as JSON (id, question, default, type, options)
 * 4. Client sends answers → optional follow-up round if needed
 * 5. Produces RequirementsDoc v1 with assumptions and requirements
 * 6. Writes requirements.md
 */
@Service
public class Clarifier {

    private static final Logger log = LoggerFactory.getLogger(Clarifier.class);
    private static final int MAX_QUESTIONS = 8;
    private static final int MAX_ROUNDS = 2;

    private final ModelRouter modelRouter;
    private final ClarifierConfig config;

    public Clarifier(ModelRouter modelRouter, ClarifierConfig config) {
        this.modelRouter = modelRouter;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("Clarifier initialized with maxQuestions={}, maxRounds={}", MAX_QUESTIONS, MAX_ROUNDS);
    }

    /**
     * Starts a new clarification session.
     * 
     * @param projectName Name of the project
     * @param userRequest Original natural language request
     * @return ClarificationSession with initial questions
     */
    public Mono<ClarificationSession> startSession(String projectName, String userRequest) {
        log.info("Starting clarification session for project: {}", projectName);
        
        String prompt = buildPlannerPrompt(userRequest);
        Map<String, Object> metadata = Map.of(
            "taskId", "clarify-" + System.currentTimeMillis(),
            "profile", "clarifier",
            "projectName", projectName
        );

        ChatRequest request = new ChatRequest(
            List.of(new Message("user", prompt, null, List.of())),
            "planner-model",
            0.1, 2048, 0.9,
            config.getSystemPrompt(),
            List.of(),
            metadata
        );

        return modelRouter.chatWithProfile(request, "clarifier")
            .map(response -> parseQuestions(response, projectName, userRequest, 1))
            .doOnNext(session -> log.info("Generated {} questions for project {}", 
                session.questions().size(), projectName));
    }

    /**
     * Processes user answers and generates follow-up questions or final RequirementsDoc.
     * 
     * @param session Current clarification session
     * @param answers Map of questionId -> userAnswer
     * @return Updated session with follow-up questions or completed RequirementsDoc
     */
    public Mono<ClarificationSession> processAnswers(ClarificationSession session, Map<String, String> answers) {
        // Update session with user answers
        ClarificationSession updatedSession = session.withAnswers(answers);
        
        // Check if we have enough info or reached max rounds
        if (updatedSession.round() >= MAX_ROUNDS || updatedSession.isComplete()) {
            log.info("Clarification complete for project {}, generating RequirementsDoc", 
                session.projectName());
            return Mono.just(generateRequirementsDoc(updatedSession));
        }

        // Generate follow-up questions
        log.info("Generating follow-up questions (round {}) for project {}", 
            updatedSession.round() + 1, session.projectName());
        
        String followUpPrompt = buildFollowUpPrompt(session);
        Map<String, Object> metadata = Map.of(
            "taskId", "clarify-followup-" + System.currentTimeMillis(),
            "profile", "clarifier",
            "projectName", session.projectName(),
            "round", updatedSession.round() + 1
        );

        ChatRequest request = new ChatRequest(
            List.of(new Message("user", followUpPrompt, null, List.of())),
            "planner-model",
            0.1, 2048, 0.9,
            config.getSystemPrompt(),
            List.of(),
            metadata
        );

        return modelRouter.chatWithProfile(request, "clarifier")
            .map(response -> parseQuestions(response, session.projectName(), 
                session.userRequest(), updatedSession.round() + 1)
                .withPreviousAnswers(updatedSession.getAllAnswers()));
    }

    /**
     * Generates the final RequirementsDoc from a completed session.
     */
    private ClarificationSession generateRequirementsDoc(ClarificationSession session) {
        List<Clarification> answeredClarifications = session.questions().stream()
            .filter(Clarification::isAnswered)
            .toList();

        List<String> assumptions = buildAssumptions(session);
        List<Requirement> requirements = buildRequirements(session);
        String refinedPrompt = buildRefinedPrompt(session);

        RequirementsDoc requirementsDoc = new RequirementsDoc(
            session.projectName(),
            "v1",
            session.userRequest(),
            answeredClarifications,
            assumptions,
            requirements,
            refinedPrompt,
            Instant.now(),
            Instant.now()
        );

        return session.withRequirementsDoc(requirementsDoc);
    }

    /**
     * Builds the initial planner prompt with comprehensive checklist.
     */
    private String buildPlannerPrompt(String userRequest) {
        return """
            You are an expert software architect. Your task is to clarify a vague software request 
            by asking targeted questions. Return ONLY a JSON array of questions.

            USER REQUEST: "%s"

            CHECKLIST - Consider these areas and ask about what's missing:
            1. ENTITIES & DATA MODEL
               - What are the core entities? (e.g., Book, Author, User)
               - What fields does each entity need? (types, constraints)
               - Relationships between entities? (OneToMany, ManyToMany, etc.)
               - Database choice? (PostgreSQL, MySQL, H2 for test)

            2. API ENDPOINTS
               - REST or GraphQL?
               - CRUD operations needed? (Create, Read, Update, Delete, List)
               - Pagination, filtering, sorting?
               - Custom endpoints?

            3. AUTHENTICATION & AUTHORIZATION
               - Auth type? (JWT, Session, OAuth2, None)
               - Roles/permissions? (ADMIN, USER, GUEST)
               - Password requirements?
               - Token expiry?

            4. VALIDATION & BUSINESS RULES
               - Field validation rules? (@NotBlank, @Size, @Pattern, custom)
               - Cross-field validation?
               - Unique constraints?

            5. SECURITY
               - CORS policy?
               - Rate limiting?
               - Audit logging?
               - Data encryption needs?

            6. TESTING & QUALITY
               - Test coverage target?
               - Integration tests with Testcontainers?
               - Contract tests?

            7. DEPLOYMENT & OPERATIONS
               - Docker / Docker Compose?
               - Kubernetes manifests?
               - Health checks / Actuator?
               - Logging format?

            8. OUT OF SCOPE
               - What is explicitly NOT needed?

            RULES:
            - Return MAX %d questions
            - Each question MUST have: id (Q-001), question, default, type (SELECT/INPUT/BOOLEAN), options (for SELECT)
            - Questions should be SPECIFIC to this request, not generic
            - Prefer SELECT with good defaults over INPUT
            - Focus on architectural decisions, not implementation details
            - If request is already specific, ask fewer questions

            OUTPUT FORMAT (JSON array only, no markdown):
            [
              {
                "id": "Q-001",
                "question": "Which database should be used?",
                "default": "PostgreSQL",
                "type": "SELECT",
                "options": ["PostgreSQL", "MySQL", "H2"]
              }
            ]
            """.formatted(userRequest, MAX_QUESTIONS);
    }

    /**
     * Builds follow-up prompt based on previous answers.
     */
    private String buildFollowUpPrompt(ClarificationSession session) {
        String answeredSummary = session.questions().stream()
            .filter(Clarification::isAnswered)
            .map(q -> "- %s: %s".formatted(q.id(), q.getEffectiveAnswer()))
            .reduce("", (a, b) -> a + "\n" + b);

        return """
            You are an expert software architect. Based on the user's answers, ask follow-up questions 
            to fill remaining gaps. Return ONLY a JSON array of questions.

            ORIGINAL REQUEST: "%s"
            PROJECT: %s
            PREVIOUS ANSWERS:
            %s

            Identify what's still unclear and ask up to %d focused follow-up questions.
            Focus on: missing entities, unclear relationships, auth details, validation rules, 
            deployment specifics, or any contradictory answers.

            RULES:
            - Return MAX %d questions
            - Each question MUST have: id (Q-XXX), question, default, type (SELECT/INPUT/BOOLEAN), options (for SELECT)
            - Questions should resolve remaining ambiguity
            - If everything is clear, return empty array: []

            OUTPUT FORMAT (JSON array only):
            [
              {
                "id": "Q-002",
                "question": "Should the API support soft deletes?",
                "default": "true",
                "type": "BOOLEAN",
                "options": []
              }
            ]
            """.formatted(
                session.userRequest(),
                session.projectName(),
                answeredSummary,
                MAX_QUESTIONS,
                MAX_QUESTIONS
            );
    }

    /**
     * Parses model response into Clarification objects.
     */
    private ClarificationSession parseQuestions(ChatResponse response, String projectName, 
            String userRequest, int round) {
        String content = response.content();
        List<Clarification> questions = new ArrayList<>();

        try {
            // Extract JSON array from response
            String json = extractJsonArray(content);
            if (json != null && !json.trim().isEmpty() && !json.trim().equals("[]")) {
                questions = parseJsonToClarifications(json);
            }
        } catch (Exception e) {
            log.warn("Failed to parse questions from model response: {}", e.getMessage());
            questions = generateFallbackQuestions(userRequest);
        }

        // Fallback if parsing returned empty list
        if (questions.isEmpty()) {
            log.warn("Parsing returned empty questions, using fallback");
            questions = generateFallbackQuestions(userRequest);
        }

        // Limit to max questions
        if (questions.size() > MAX_QUESTIONS) {
            questions = questions.subList(0, MAX_QUESTIONS);
        }

        // Ensure sequential IDs
        for (int i = 0; i < questions.size(); i++) {
            Clarification q = questions.get(i);
            String expectedId = "Q-%03d".formatted(i + 1 + (round - 1) * MAX_QUESTIONS);
            if (!q.id().equals(expectedId)) {
                questions.set(i, new Clarification(
                    expectedId, q.question(), q.defaultAnswer(), q.userAnswer(), 
                    q.type(), q.options()));
            }
        }

        return new ClarificationSession(
            projectName, userRequest, questions, round, Map.of(), null);
    }

    /**
     * Extracts JSON array from model response.
     */
    private String extractJsonArray(String content) {
        // Try to find JSON array in the response
        Pattern pattern = Pattern.compile("\\[\\s*\\{.*\\}\\s*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group();
        }
        return content.trim();
    }

    /**
     * Parses JSON array to Clarification list.
     */
    private List<Clarification> parseJsonToClarifications(String json) {
        // Simple JSON parsing - in production use Jackson
        List<Clarification> result = new ArrayList<>();
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        
        // Split by } while handling nested objects
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
                Clarification q = parseClarification(obj);
                if (q != null) result.add(q);
            } catch (Exception e) {
                log.debug("Failed to parse question: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Parses a single question JSON object.
     */
    private Clarification parseClarification(String json) {
        String id = extractValue(json, "id");
        String question = extractValue(json, "question");
        String defaultAnswer = extractValue(json, "default");
        String typeStr = extractValue(json, "type");
        List<String> options = extractOptions(json);

        if (id == null || question == null) return null;

        ClarificationType type = ClarificationType.INPUT;
        if ("SELECT".equalsIgnoreCase(typeStr)) type = ClarificationType.SELECT;
        else if ("BOOLEAN".equalsIgnoreCase(typeStr)) type = ClarificationType.BOOLEAN;

        return new Clarification(
            id, question, 
            defaultAnswer != null ? defaultAnswer : "",
            null, // userAnswer - will be set later
            type,
            options
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

    private List<String> extractOptions(String json) {
        List<String> options = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"options\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            String optionsStr = matcher.group(1);
            for (String opt : optionsStr.split(",")) {
                String cleaned = opt.trim().replaceAll("^\"|\"$", "");
                if (!cleaned.isEmpty()) options.add(cleaned);
            }
        }
        return options;
    }

    /**
     * Generates sensible fallback questions if model fails.
     */
    private List<Clarification> generateFallbackQuestions(String userRequest) {
        return List.of(
            new Clarification("Q-001", "Which database should be used?", "PostgreSQL", null,
                ClarificationType.SELECT, List.of("PostgreSQL", "MySQL", "H2")),
            new Clarification("Q-002", "What authentication type is needed?", "JWT", null,
                ClarificationType.SELECT, List.of("JWT", "Session", "None")),
            new Clarification("Q-003", "Should the API support pagination?", "true", null,
                ClarificationType.BOOLEAN, List.of()),
            new Clarification("Q-004", "What is the primary entity name?", "Book", null,
                ClarificationType.INPUT, List.of())
        );
    }

    /**
     * Builds assumptions list from answers.
     */
    private List<String> buildAssumptions(ClarificationSession session) {
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Spring Boot 3.4+ with Java 21");
        assumptions.add("Maven for build management");
        assumptions.add("JPA/Hibernate for ORM");
        assumptions.add("JUnit 5 + Testcontainers for testing");
        assumptions.add("Checkstyle, SpotBugs, JaCoCo for quality gates");

        // Add assumptions from answers
        for (Clarification q : session.questions()) {
            if (q.isAnswered()) {
                String answer = q.getEffectiveAnswer();
                assumptions.add("%s: %s".formatted(q.question(), answer));
            }
        }
        return assumptions;
    }

    /**
     * Builds structured requirements from answers.
     */
    private List<Requirement> buildRequirements(ClarificationSession session) {
        List<Requirement> requirements = new ArrayList<>();
        int reqNum = 1;

        // Functional requirements from answers
        for (Clarification q : session.questions()) {
            if (q.isAnswered()) {
                String category = categorizeQuestion(q.question());
                requirements.add(new Requirement(
                    "R-%03d".formatted(reqNum++),
                    category,
                    "%s: %s".formatted(q.question(), q.getEffectiveAnswer()),
                    Priority.HIGH
                ));
            }
        }

        // Add standard requirements
        requirements.add(new Requirement(
            "R-%03d".formatted(reqNum++),
            "SECURITY",
            "All endpoints must validate input with @Valid",
            Priority.HIGH
        ));
        requirements.add(new Requirement(
            "R-%03d".formatted(reqNum++),
            "SECURITY",
            "Passwords must be hashed with BCrypt",
            Priority.HIGH
        ));
        requirements.add(new Requirement(
            "R-%03d".formatted(reqNum++),
            "TESTING",
            "Unit tests for all services, integration tests for controllers",
            Priority.MEDIUM
        ));
        requirements.add(new Requirement(
            "R-%03d".formatted(reqNum++),
            "DOCUMENTATION",
            "OpenAPI/Swagger documentation for all endpoints",
            Priority.MEDIUM
        ));

        return requirements;
    }

    private String categorizeQuestion(String question) {
        String lower = question.toLowerCase();
        if (lower.contains("database") || lower.contains("entity") || lower.contains("field") || lower.contains("relationship")) {
            return "DATA_MODEL";
        }
        if (lower.contains("endpoint") || lower.contains("api") || lower.contains("rest") || lower.contains("graphql")) {
            return "FUNCTIONAL";
        }
        if (lower.contains("auth") || lower.contains("role") || lower.contains("permission") || lower.contains("password") || lower.contains("token")) {
            return "SECURITY";
        }
        if (lower.contains("valid") || lower.contains("constraint") || lower.contains("unique")) {
            return "VALIDATION";
        }
        if (lower.contains("test") || lower.contains("coverage")) {
            return "TESTING";
        }
        if (lower.contains("deploy") || lower.contains("docker") || lower.contains("kubernetes") || lower.contains("health")) {
            return "OPERATIONS";
        }
        return "FUNCTIONAL";
    }

    /**
     * Builds the refined prompt for code generation.
     */
    private String buildRefinedPrompt(ClarificationSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal: Generate a Spring Boot REST API based on clarified requirements\n\n");
        sb.append("Fixed Stack: Java 21, Spring Boot 3.4, Maven, PostgreSQL, JWT\n\n");
        
        // Entities from answers
        sb.append("Entities: ");
        for (Clarification q : session.questions()) {
            if (q.isAnswered() && q.question().toLowerCase().contains("entity")) {
                sb.append(q.getEffectiveAnswer()).append(", ");
            }
        }
        sb.append("Book (id, title, author, genre, isbn)\n");
        
        // Endpoints
        sb.append("Endpoints: GET/POST /api/books, GET/PUT/DELETE /api/books/{id}\n");
        
        // Security from answers
        boolean hasAuth = session.questions().stream()
            .anyMatch(q -> q.question().toLowerCase().contains("auth") && q.isAnswered());
        if (hasAuth) {
            sb.append("Security Rules: JWT auth, BCrypt passwords, parameterized queries\n");
        }
        
        sb.append("Validation Rules: @Valid on DTOs, ISBN format validation\n");
        sb.append("Testing Rules: JUnit 5, Testcontainers, 80% coverage\n");
        sb.append("Conventions: Constructor injection, records for DTOs, MapStruct\n");
        sb.append("Out of Scope: GraphQL, WebSocket, Admin UI\n");
        sb.append("Acceptance: mvn verify passes, 0 HIGH findings\n");

        return sb.toString();
    }
}