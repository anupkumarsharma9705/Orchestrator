package com.example.orchestrator.core.refiner;

import com.example.orchestrator.core.config.CoreAutoConfiguration;
import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.ClarificationType;
import com.example.orchestrator.model.Requirement;
import com.example.orchestrator.model.RequirementsDoc;
import com.example.orchestrator.model.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Refiner component.
 */
@SpringBootTest(classes = {CoreAutoConfiguration.class, RefinerTestConfig.class})
@ActiveProfiles("test")
class RefinerTest {

    @Autowired
    private Refiner refiner;

    @Test
    void refine_generatesPromptWithAllSections() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        assertThat(result).isNotNull();
        assertThat(result.projectName()).isEqualTo("library");
        assertThat(result.version()).isEqualTo("v1");
        assertThat(result.prompt()).isNotBlank();
        assertThat(result.validation()).isNotNull();
        
        // Verify all 10 sections present
        String prompt = result.prompt();
        assertThat(prompt).contains("Goal:");
        assertThat(prompt).contains("Fixed Stack:");
        assertThat(prompt).contains("Entities:");
        assertThat(prompt).contains("Endpoints:");
        assertThat(prompt).contains("Security Rules:");
        assertThat(prompt).contains("Validation Rules:");
        assertThat(prompt).contains("Testing Rules:");
        assertThat(prompt).contains("Conventions:");
        assertThat(prompt).contains("Out of Scope:");
        assertThat(prompt).contains("Acceptance:");
    }

    @Test
    void refine_validationPasses_forCompleteRequirements() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        assertThat(result.validation().valid()).isTrue();
        assertThat(result.validation().errors()).isEmpty();
    }

    @Test
    void refine_fixedStackFromConfig_notModel() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        String prompt = result.prompt();
        // Fixed stack should contain config values, not model-generated
        assertThat(prompt).contains("Java 21");
        assertThat(prompt).contains("Spring Boot 3.4");
        assertThat(prompt).contains("Maven");
        assertThat(prompt).contains("PostgreSQL");
        assertThat(prompt).contains("JWT");
    }

    @Test
    void refine_securityRulesFromConfig() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        String prompt = result.prompt();
        assertThat(prompt).contains("JWT auth");
        assertThat(prompt).contains("BCrypt");
        assertThat(prompt).contains("parameterized queries");
    }

    @Test
    void refine_validationRulesFromConfig() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        String prompt = result.prompt();
        assertThat(prompt).contains("@Valid");
        assertThat(prompt).contains("@Size");
    }

    @Test
    void refine_testingRulesFromConfig() {
        RequirementsDoc doc = createSampleRequirementsDoc();
        
        Refiner.RefinedPrompt result = refiner.refine(doc).block();
        
        String prompt = result.prompt();
        assertThat(prompt).contains("JUnit 5");
        assertThat(prompt).contains("Testcontainers");
        assertThat(prompt).contains("80%");
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
}