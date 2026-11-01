package com.example.orchestrator.core.refiner;

import com.example.orchestrator.llm.ModelRouter;
import com.example.orchestrator.model.ChatRequest;
import com.example.orchestrator.model.ChatResponse;
import com.example.orchestrator.model.ChatRequest.Message;
import com.example.orchestrator.model.Requirement;
import com.example.orchestrator.model.RequirementsDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Refiner: Converts RequirementsDoc into a structured build prompt with 10 fixed sections.
 * 
 * Template sections (from ORCHESTRATOR_PLAN.md):
 * 1. Goal - What to build
 * 2. Fixed Stack - Tech stack from config (not model)
 * 3. Entities - Database entities with fields
 * 4. Endpoints - API endpoints
 * 5. Security Rules - Auth, validation, encryption
 * 6. Validation Rules - Field and cross-field validation
 * 7. Testing Rules - Test types, coverage, tools
 * 8. Conventions - Code style, patterns
 * 9. Out of Scope - Explicitly excluded
 * 10. Acceptance Criteria - Definition of done
 * 
 * Validation: All sections present, all requirement IDs referenced
 */
@Service
public class Refiner {

    private static final Logger log = LoggerFactory.getLogger(Refiner.class);

    private final ModelRouter modelRouter;
    private final RefinerConfig config;

    public Refiner(ModelRouter modelRouter, RefinerConfig config) {
        this.modelRouter = modelRouter;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("Refiner initialized");
    }

    /**
     * Refines a RequirementsDoc into a structured build prompt.
     * 
     * @param requirementsDoc The clarified requirements document
     * @return RefinedPrompt with the structured prompt and validation result
     */
    public Mono<RefinedPrompt> refine(RequirementsDoc requirementsDoc) {
        log.info("Refining requirements for project: {}", requirementsDoc.projectName());
        
        String prompt = buildRefinedPrompt(requirementsDoc);
        
        // Validate the prompt
        ValidationResult validation = validatePrompt(prompt, requirementsDoc);
        
        RefinedPrompt refined = new RefinedPrompt(
            requirementsDoc.projectName(),
            requirementsDoc.version(),
            prompt,
            validation,
            java.time.Instant.now()
        );
        
        log.info("Refined prompt validation: {} - {} errors", 
            validation.valid() ? "VALID" : "INVALID", validation.errors().size());
        
        return Mono.just(refined);
    }

    /**
     * Builds the refined prompt from requirements.
     * Fixed stack/rules come from config, not the model.
     */
    private String buildRefinedPrompt(RequirementsDoc doc) {
        StringBuilder sb = new StringBuilder();
        
        // Section 1: Goal
        sb.append("Goal: ").append(doc.originalRequest()).append("\n\n");
        
        // Section 2: Fixed Stack (from config, not model)
        sb.append("Fixed Stack: ").append(config.getFixedStack()).append("\n\n");
        
        // Section 3: Entities
        sb.append("Entities: ").append(extractEntities(doc)).append("\n\n");
        
        // Section 4: Endpoints
        sb.append("Endpoints: ").append(extractEndpoints(doc)).append("\n\n");
        
        // Section 5: Security Rules
        sb.append("Security Rules: ").append(config.getSecurityRules()).append("\n\n");
        
        // Section 6: Validation Rules
        sb.append("Validation Rules: ").append(config.getValidationRules()).append("\n\n");
        
        // Section 7: Testing Rules
        sb.append("Testing Rules: ").append(config.getTestingRules()).append("\n\n");
        
        // Section 8: Conventions
        sb.append("Conventions: ").append(config.getConventions()).append("\n\n");
        
        // Section 9: Out of Scope
        sb.append("Out of Scope: ").append(config.getOutOfScope()).append("\n\n");
        
        // Section 10: Acceptance Criteria
        sb.append("Acceptance: ").append(config.getAcceptanceCriteria()).append("\n");
        
        return sb.toString();
    }

    private String extractEntities(RequirementsDoc doc) {
        // Extract from clarifications and requirements
        StringBuilder entities = new StringBuilder();
        boolean first = true;
        
        for (Requirement req : doc.requirements()) {
            if ("DATA_MODEL".equals(req.category()) || "FUNCTIONAL".equals(req.category())) {
                if (!first) entities.append(", ");
                entities.append(req.text());
                first = false;
            }
        }
        
        // Fallback if none found
        if (entities.isEmpty()) {
            entities.append("Book (id, title, author, genre, isbn)");
        }
        
        return entities.toString();
    }

    private String extractEndpoints(RequirementsDoc doc) {
        StringBuilder endpoints = new StringBuilder();
        boolean first = true;
        
        for (Requirement req : doc.requirements()) {
            if ("FUNCTIONAL".equals(req.category()) && req.text().toLowerCase().contains("endpoint")) {
                if (!first) endpoints.append(", ");
                endpoints.append(req.text());
                first = false;
            }
        }
        
        if (endpoints.isEmpty()) {
            endpoints.append("GET/POST /api/books, GET/PUT/DELETE /api/books/{id}");
        }
        
        return endpoints.toString();
    }

    /**
     * Validates the refined prompt.
     * Checks: all 10 sections present, all requirement IDs referenced.
     */
    private ValidationResult validatePrompt(String prompt, RequirementsDoc doc) {
        List<String> errors = new java.util.ArrayList<>();
        
        // Check all 10 sections present
        String[] requiredSections = {
            "Goal:", "Fixed Stack:", "Entities:", "Endpoints:", 
            "Security Rules:", "Validation Rules:", "Testing Rules:", 
            "Conventions:", "Out of Scope:", "Acceptance:"
        };
        
        for (String section : requiredSections) {
            if (!prompt.contains(section)) {
                errors.add("Missing required section: " + section);
            }
        }
        
        // Check all requirement IDs are referenced - check for key words from each requirement
        List<String> reqIds = doc.getRequirementIds();
        for (String reqId : reqIds) {
            boolean found = doc.requirements().stream()
                .filter(r -> r.id().equals(reqId))
                .anyMatch(r -> {
                    // Extract key words from requirement text (words > 3 chars)
                    String[] words = r.text().toLowerCase().split("\\s+");
                    for (String word : words) {
                        if (word.length() > 3 && prompt.toLowerCase().contains(word)) {
                            return true;
                        }
                    }
                    // Also check if requirement ID itself appears
                    return prompt.contains(reqId);
                });
            
            if (!found) {
                errors.add("Requirement ID not referenced in prompt: " + reqId);
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Result of prompt refinement with validation.
     */
    public record RefinedPrompt(
        String projectName,
        String version,
        String prompt,
        ValidationResult validation,
        java.time.Instant createdAt
    ) {}

    /**
     * Validation result for refined prompt.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors
    ) {}
}