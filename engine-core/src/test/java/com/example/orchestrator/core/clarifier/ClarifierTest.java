package com.example.orchestrator.core.clarifier;

import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.model.Clarification;
import com.example.orchestrator.model.ClarificationType;
import com.example.orchestrator.model.Requirement;
import com.example.orchestrator.model.RequirementsDoc;
import com.example.orchestrator.model.Priority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the Clarifier component.
 * Uses TestModelProvider (profile "test") for canned responses.
 */
@SpringBootTest(classes = {com.example.orchestrator.core.config.CoreAutoConfiguration.class, ClarifierTestConfig.class})
@ActiveProfiles("test")
class ClarifierTest {

    @Autowired
    private Clarifier clarifier;

    @Test
    void startSession_generatesQuestions_forBookCrudRequest() {
        String request = "REST CRUD for Book entity with author, genre, ISBN, pagination, validation";
        
        ClarificationSession session = clarifier.startSession("library", request).block();

        assertThat(session).isNotNull();
        assertThat(session.projectName()).isEqualTo("library");
        assertThat(session.userRequest()).isEqualTo(request);
        assertThat(session.round()).isEqualTo(1);
        assertThat(session.questions()).hasSizeBetween(1, 8);
        
        // Verify question structure
        for (Clarification q : session.questions()) {
            assertThat(q.id()).matches("Q-\\d{3}");
            assertThat(q.question()).isNotBlank();
            assertThat(q.type()).isNotNull();
            if (q.type() == ClarificationType.SELECT) {
                assertThat(q.options()).isNotEmpty();
            }
        }
    }

    @Test
    void processAnswers_withAllAnswers_generatesRequirementsDoc() {
        String request = "REST CRUD for books with login";
        
        // Start session
        ClarificationSession session = clarifier.startSession("library", request).block();
        
        // Provide answers for all questions
        Map<String, String> answers = session.questions().stream()
            .collect(java.util.stream.Collectors.toMap(
                Clarification::id,
                Clarification::defaultAnswer // Use default as answer
            ));
        
        ClarificationSession completed = clarifier.processAnswers(session, answers).block();

        assertThat(completed).isNotNull();
        assertThat(completed.requirementsDoc()).isNotNull();
        
        RequirementsDoc doc = completed.requirementsDoc();
        assertThat(doc.projectName()).isEqualTo("library");
        assertThat(doc.version()).isEqualTo("v1");
        assertThat(doc.originalRequest()).isEqualTo(request);
        assertThat(doc.clarifications()).isNotEmpty();
        assertThat(doc.assumptions()).isNotEmpty();
        assertThat(doc.requirements()).isNotEmpty();
        assertThat(doc.refinedPrompt()).isNotBlank();
    }

    @Test
    void requirementsDoc_hasValidStructure() {
        String request = "REST CRUD for books with JWT authentication";
        
        ClarificationSession session = clarifier.startSession("library", request).block();
        
        Map<String, String> answers = session.questions().stream()
            .collect(java.util.stream.Collectors.toMap(
                Clarification::id,
                Clarification::defaultAnswer
            ));
        
        ClarificationSession completed = clarifier.processAnswers(session, answers).block();
        
        RequirementsDoc doc = completed.requirementsDoc();
        
        // Verify all clarifications have effective answers
        for (Clarification q : doc.clarifications()) {
            assertThat(q.isAnswered()).isTrue();
            assertThat(q.getEffectiveAnswer()).isNotBlank();
        }
        
        // Verify requirements have valid IDs and categories
        for (Requirement req : doc.requirements()) {
            assertThat(req.id()).matches("R-\\d{3,}");
            assertThat(req.category()).isNotBlank();
            assertThat(req.text()).isNotBlank();
            assertThat(req.priority()).isNotNull();
        }
        
        // Verify refined prompt contains key sections
        String prompt = doc.refinedPrompt();
        assertThat(prompt).contains("Goal:");
        assertThat(prompt).contains("Fixed Stack:");
        assertThat(prompt).contains("Entities:");
        assertThat(prompt).contains("Endpoints:");
        assertThat(prompt).contains("Acceptance:");
    }

    @Test
    void processAnswers_followUpRound_whenNotComplete() {
        String request = "Build a book management system";
        
        ClarificationSession session = clarifier.startSession("books", request).block();
        
        // Answer only first question
        Map<String, String> partialAnswers = Map.of(
            session.questions().get(0).id(), "PostgreSQL"
        );
        
        ClarificationSession round2 = clarifier.processAnswers(session, partialAnswers).block();
        
        assertThat(round2.round()).isEqualTo(2);
        assertThat(round2.questions()).isNotEmpty();
        assertThat(round2.getAllAnswers()).containsEntry(
            session.questions().get(0).id(), "PostgreSQL"
        );
    }

    @Test
    void clarificationSession_isComplete_whenAllAnswered() {
        ClarificationSession session = new ClarificationSession(
            "test", "request",
            List.of(
                new Clarification("Q-001", "Question 1?", "default1", "answer1", 
                    ClarificationType.SELECT, List.of("a", "b")),
                new Clarification("Q-002", "Question 2?", "default2", "answer2", 
                    ClarificationType.INPUT, List.of())
            ),
            1, Map.of("Q-001", "answer1", "Q-002", "answer2"), null
        );
        
        assertThat(session.isComplete()).isTrue();
    }

    @Test
    void clarificationSession_getAllAnswers_mergesUserAndDefaults() {
        ClarificationSession session = new ClarificationSession(
            "test", "request",
            List.of(
                new Clarification("Q-001", "Question 1?", "default1", "user1", 
                    ClarificationType.SELECT, List.of("a", "b")),
                new Clarification("Q-002", "Question 2?", "default2", null, 
                    ClarificationType.INPUT, List.of())
            ),
            1, Map.of("Q-001", "user1"), null
        );
        
        Map<String, String> all = session.getAllAnswers();
        assertThat(all.get("Q-001")).isEqualTo("user1");
        assertThat(all.get("Q-002")).isEqualTo("default2");
    }
}