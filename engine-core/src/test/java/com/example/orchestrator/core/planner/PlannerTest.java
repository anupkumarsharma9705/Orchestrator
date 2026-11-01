package com.example.orchestrator.core.planner;

import com.example.orchestrator.core.config.CoreAutoConfiguration;
import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.ClarificationType;
import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.Requirement;
import com.example.orchestrator.model.RequirementsDoc;
import com.example.orchestrator.model.Priority;
import com.example.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Planner component.
 */
@SpringBootTest(classes = {CoreAutoConfiguration.class, PlannerTestConfig.class})
@ActiveProfiles("test")
class PlannerTest {

    @Autowired
    private Planner planner;

    @Test
    void createPlan_generatesTasksWithAllFields() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        String refinedPrompt = createSampleRefinedPrompt();
        
        Plan plan = planner.createPlan(doc, refinedPrompt).block();
        
        assertThat(plan).isNotNull();
        assertThat(plan.projectName()).isEqualTo("library");
        assertThat(plan.version()).isEqualTo("v1");
        assertThat(plan.tasks()).hasSizeBetween(5, 15);
        
        // Verify each task has all required fields
        for (PlanTask task : plan.tasks()) {
            assertThat(task.id()).matches("T-\\d{3}");
            assertThat(task.title()).isNotBlank();
            assertThat(task.description()).isNotBlank();
            assertThat(task.files()).isNotEmpty();
            assertThat(task.dependsOn()).isNotNull();
            assertThat(task.requirementIds()).isNotEmpty();
            assertThat(task.acceptance()).isNotEmpty();
            assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
        }
    }

    @Test
    void createPlan_tasksInTopologicalOrder() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        String refinedPrompt = createSampleRefinedPrompt();
        
        Plan plan = planner.createPlan(doc, refinedPrompt).block();
        
        // Verify no cycles - getTopologicalOrder should succeed
        List<PlanTask> sorted = plan.getTopologicalOrder();
        assertThat(sorted).hasSameSizeAs(plan.tasks());
        
        // Verify dependencies come before dependents
        for (int i = 0; i < sorted.size(); i++) {
            PlanTask task = sorted.get(i);
            for (String depId : task.dependsOn()) {
                int depIndex = sorted.indexOf(sorted.stream().filter(t -> t.id().equals(depId)).findFirst().orElse(null));
                assertThat(depIndex).isLessThan(i).as("Dependency %s should come before %s", depId, task.id());
            }
        }
    }

    @Test
    void createPlan_allRequirementIdsCovered() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        String refinedPrompt = createSampleRefinedPrompt();
        
        Plan plan = planner.createPlan(doc, refinedPrompt).block();
        
        // Collect all requirement IDs from tasks
        List<String> coveredReqs = plan.tasks().stream()
            .flatMap(t -> t.requirementIds().stream())
            .distinct()
            .toList();
        
        // Verify all requirement IDs from doc are covered
        for (Requirement req : doc.requirements()) {
            assertThat(coveredReqs).contains(req.id())
                .as("Requirement %s should be covered by at least one task", req.id());
        }
    }

    @Test
    void createPlan_noCyclesInDependencies() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        String refinedPrompt = createSampleRefinedPrompt();
        
        Plan plan = planner.createPlan(doc, refinedPrompt).block();
        
        // validate() should not throw
        Plan.ValidationResult validation = plan.validate();
        assertThat(validation.valid()).isTrue();
        assertThat(validation.errors()).isEmpty();
    }

    @Test
    void createPlan_filesAreRelativePaths() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        String refinedPrompt = createSampleRefinedPrompt();
        
        Plan plan = planner.createPlan(doc, refinedPrompt).block();
        
        for (PlanTask task : plan.tasks()) {
            for (String file : task.files()) {
                assertThat(file).doesNotStartWith("/")
                    .as("File path should be relative: %s", file);
                assertThat(file).doesNotContain("..")
                    .as("File path should not contain parent references: %s", file);
            }
        }
    }

    @Test
    void planValidation_detectsCycles() {
        // Create a plan with a cycle manually
        Instant now = Instant.now();
        List<PlanTask> cyclicTasks = List.of(
            new PlanTask("T-001", "Task 1", "Desc", List.of("a.java"), List.of("T-002"), List.of("R-001"), List.of("OK"), TaskStatus.PENDING, now, now),
            new PlanTask("T-002", "Task 2", "Desc", List.of("b.java"), List.of("T-001"), List.of("R-002"), List.of("OK"), TaskStatus.PENDING, now, now)
        );
        Plan cyclicPlan = new Plan("test", "v1", cyclicTasks, now, now);
        
        Plan.ValidationResult validation = cyclicPlan.validate();
        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).anyMatch(e -> e.contains("Cycle"));
    }

    private RequirementsDoc createSampleRequirementsDoc() {
        return new RequirementsDoc(
            "library",
            "v1",
            "REST CRUD for books with JWT authentication",
            List.of(
                new Clarification("Q-001", "Which database?", "PostgreSQL", "PostgreSQL", 
                    ClarificationType.SELECT, List.of("PostgreSQL", "MySQL", "H2")),
                new Clarification("Q-002", "Auth type?", "JWT", "JWT", 
                    ClarificationType.SELECT, List.of("JWT", "Session", "None")),
                new Clarification("Q-003", "Primary entity?", "Book", "Book", 
                    ClarificationType.INPUT, List.of())
            ),
            List.of("Spring Boot 3.4+ with Java 21", "Maven for build management"),
            List.of(
                new Requirement("R-001", "DATA_MODEL", "Book entity with title, author, genre, isbn", Priority.HIGH),
                new Requirement("R-002", "FUNCTIONAL", "GET/POST /api/books, GET/PUT/DELETE /api/books/{id}", Priority.HIGH),
                new Requirement("R-003", "SECURITY", "JWT authentication with BCrypt passwords", Priority.HIGH)
            ),
            "Test refined prompt",
            Instant.now(),
            Instant.now()
        );
    }

    private String createSampleRefinedPrompt() {
        return """
            Goal: Generate a Spring Boot REST API based on clarified requirements
            
            Fixed Stack: Java 21, Spring Boot 3.4, Maven, PostgreSQL, JWT
            
            Entities: Book (id, title, author, genre, isbn)
            
            Endpoints: GET/POST /api/books, GET/PUT/DELETE /api/books/{id}
            
            Security Rules: JWT auth, BCrypt passwords, parameterized queries
            
            Validation Rules: @Valid on DTOs, ISBN format validation
            
            Testing Rules: JUnit 5, Testcontainers, 80% coverage
            
            Conventions: Constructor injection, records for DTOs, MapStruct
            
            Out of Scope: GraphQL, WebSocket, Admin UI
            
            Acceptance: mvn verify passes, 0 HIGH findings
            """;
    }
}