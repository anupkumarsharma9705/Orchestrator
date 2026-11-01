package com.example.orchestrator.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRecordsTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void requirement_jsonRoundTrip() {
        Requirement original = new Requirement(
            "R-001", "FUNCTIONAL", "CRUD operations for Book entity", Priority.HIGH);

        String json = toJson(original);
        Requirement deserialized = fromJson(json, Requirement.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.isValidId()).isTrue();
    }

    @Test
    void requirement_invalidId_returnsFalse() {
        Requirement req = new Requirement("invalid", "FUNCTIONAL", "Test", Priority.LOW);
        assertThat(req.isValidId()).isFalse();
    }

    @Test
    void requirementsDoc_jsonRoundTrip() {
        Instant now = Instant.now();
        Clarification clarification = new Clarification(
            "Q-001", "Which database?", "PostgreSQL", "PostgreSQL", 
            ClarificationType.SELECT, List.of("PostgreSQL", "MySQL", "H2"));

        RequirementsDoc original = new RequirementsDoc(
            "library", "1.0", "REST CRUD for Book",
            List.of(clarification),
            List.of("Java 21, Spring Boot 3.4"),
            List.of(new Requirement("R-001", "FUNCTIONAL", "CRUD for Book", Priority.HIGH)),
            "Refined prompt...",
            Instant.now(), Instant.now());

        String json = toJson(original);
        RequirementsDoc deserialized = fromJson(json, RequirementsDoc.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.getRequirementIds()).containsExactly("R-001");
    }

    @Test
    void planTask_jsonRoundTrip() {
        PlanTask original = new PlanTask(
            "T-001", "Create Book entity", "Create JPA entity for Book",
            List.of("src/main/java/.../Book.java"),
            List.of(), List.of("R-001"),
            List.of("Entity compiles", "Tests pass"),
            TaskStatus.PENDING, Instant.now(), Instant.now());

        String json = toJson(original);
        PlanTask deserialized = fromJson(json, PlanTask.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void plan_jsonRoundTrip_andValidation() {
        PlanTask task1 = new PlanTask("T-001", "Entity", "Create entity",
            List.of("Entity.java"), List.of(), List.of("R-001"),
            List.of("Compiles"), TaskStatus.PENDING, Instant.now(), Instant.now());
        PlanTask task2 = new PlanTask("T-002", "Repository", "Create repo",
            List.of("Repo.java"), List.of("T-001"), List.of("R-001"),
            List.of("Compiles"), TaskStatus.PENDING, Instant.now(), Instant.now());

        Plan original = new Plan("library", "1.0", List.of(task1, task2), Instant.now(), Instant.now());

        String json = toJson(original);
        Plan deserialized = fromJson(json, Plan.class);

        assertThat(deserialized).isEqualTo(original);
        
        Plan.ValidationResult validation = deserialized.validate();
        assertThat(validation.valid()).isTrue();
    }

    @Test
    void plan_detectsCycle() {
        PlanTask task1 = new PlanTask("T-001", "A", "A", List.of(), List.of("T-002"), List.of(), List.of(), TaskStatus.PENDING, Instant.now(), Instant.now());
        PlanTask task2 = new PlanTask("T-002", "B", "B", List.of(), List.of("T-001"), List.of(), List.of(), TaskStatus.PENDING, Instant.now(), Instant.now());

        Plan plan = new Plan("test", "1.0", List.of(task1, task2), Instant.now(), Instant.now());

        Plan.ValidationResult validation = plan.validate();
        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).anyMatch(e -> e.contains("Cycle"));
    }

    @Test
    void finding_jsonRoundTrip() {
        Finding original = new Finding(
            "fp123", "semgrep", "java.sql-injection", Severity.HIGH,
            "src/main/java/BookRepository.java", 42,
            "SQL injection risk", 1, null, FindingStatus.OPEN, Instant.now());

        String json = toJson(original);
        Finding deserialized = fromJson(json, Finding.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.isOpen()).isTrue();
        assertThat(deserialized.isFixed()).isFalse();
    }

    @Test
    void fix_jsonRoundTrip() {
        Fix original = new Fix(
            "fp123", 2, "Fixed SQL injection by using parameterized query",
            List.of("src/main/java/BookRepository.java"),
            FixResult.VERIFIED_FIXED, Instant.now());

        String json = toJson(original);
        Fix deserialized = fromJson(json, Fix.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.isSuccessful()).isTrue();
    }

    @Test
    void job_jsonRoundTrip() {
        Job original = new Job(
            "job-123", "proj-1", JobState.BUILDING, 2,
            new BigDecimal("0.50"), new BigDecimal("0.10"),
            Instant.now(), null, null);

        String json = toJson(original);
        Job deserialized = fromJson(json, Job.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.isBudgetExceeded()).isFalse();
        assertThat(deserialized.isRunning()).isTrue();
    }

    @Test
    void job_detectsBudgetExceeded() {
        Job job = new Job("job-1", "p1", JobState.BUILDING, 1,
            new BigDecimal("0.10"), new BigDecimal("0.20"),
            Instant.now(), null, null);

        assertThat(job.isBudgetExceeded()).isTrue();
    }

    @Test
    void round_jsonRoundTrip() {
        Round original = new Round(
            "round-1", "job-1", 1, true, 10, 0,
            new BigDecimal("85.5"), new BigDecimal("0.01"), 30,
            Instant.now(), Instant.now().plusSeconds(30));

        String json = toJson(original);
        Round deserialized = fromJson(json, Round.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.isSuccessful()).isTrue();
    }

    @Test
    void taskCost_jsonRoundTrip() {
        TaskCost original = new TaskCost(
            "cost-1", "task-1", "nvidia/nemotron-3-ultra", "nvidia",
            1000, 500, new BigDecimal("0.001"),
            Instant.now(), Instant.now());

        String json = toJson(original);
        TaskCost deserialized = fromJson(json, TaskCost.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.totalTokens()).isEqualTo(1500);
    }

    @Test
    void dailyCost_jsonRoundTrip() {
        DailyCost original = new DailyCost(
            java.time.LocalDate.now(), 10000, 5000,
            new BigDecimal("0.05"), Instant.now());

        String json = toJson(original);
        DailyCost deserialized = fromJson(json, DailyCost.class);

        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.totalTokens()).isEqualTo(15000);
    }

    @Test
    void approval_jsonRoundTrip() {
        Approval original = new Approval(
            "appr-1", "job-1", ApprovalStatus.APPROVED,
            "tech-lead", "Looks good", Instant.now(), Instant.now(), Instant.now());

        String json = toJson(original);
        Approval deserialized = fromJson(json, Approval.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void project_jsonRoundTrip() {
        Project original = new Project(
            "proj-1", "library", "REST CRUD for Book entity",
            "com.example.library", "Library",
            "/path/to/library", "/path/to/.companion",
            "MAVEN", "mvn verify", java.util.Map.of("DB_URL", "jdbc:postgresql://localhost:5432/db"),
            Instant.now(), Instant.now());

        String json = toJson(original);
        Project deserialized = fromJson(json, Project.class);

        assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void projectMemory_jsonRoundTrip() {
        ProjectMemory original = new ProjectMemory(
            "mem-1", "proj-1", "CONVENTION", "naming",
            "Use camelCase for variables", 10, Instant.now(), Instant.now());

        String json = toJson(original);
        ProjectMemory deserialized = fromJson(json, ProjectMemory.class);

        assertThat(deserialized).isEqualTo(original);
    }
}