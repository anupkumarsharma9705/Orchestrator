package com.example.orchestrator.eval.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A golden specification for evaluation.
 * 
 * @param id Unique identifier (e.g., "CRUD-001", "AUTH-001", "BIZ-001")
 * @param category Category: CRUD, AUTH_RELATIONS, BUSINESS_RULES
 * @param name Human-readable name
 * @param description Natural language description of the project to generate
 * @param expectedEntities List of expected entity names
 * @param expectedEndpoints List of expected endpoint patterns (e.g., "GET /api/books")
 * @param expectedSecurity Expected security features (e.g., "JWT", "ROLE_BASED")
 * @param expectedTests Expected test coverage or test types
 * @param fixedAnswers Pre-defined answers for clarifier questions (JSON array)
 * @param timeoutSec Maximum time to complete generation
 * @param createdAt Timestamp when created
 */
public record GoldenSpec(
    @NotBlank String id,
    @NotBlank String category,
    @NotBlank String name,
    @NotBlank String description,
    @Size(min = 1) List<String> expectedEntities,
    @Size(min = 1) List<String> expectedEndpoints,
    @Size(min = 1) List<String> expectedSecurity,
    @Size(min = 1) List<String> expectedTests,
    String fixedAnswers,
    int timeoutSec,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt
) {
    public GoldenSpec {
        expectedEntities = expectedEntities != null ? List.copyOf(expectedEntities) : List.of();
        expectedEndpoints = expectedEndpoints != null ? List.copyOf(expectedEndpoints) : List.of();
        expectedSecurity = expectedSecurity != null ? List.copyOf(expectedSecurity) : List.of();
        expectedTests = expectedTests != null ? List.copyOf(expectedTests) : List.of();
        fixedAnswers = fixedAnswers != null ? fixedAnswers : "[]";
        timeoutSec = timeoutSec > 0 ? timeoutSec : 300;
    }

    /**
     * Checks if this spec is a CRUD spec.
     */
    public boolean isCrud() {
        return "CRUD".equalsIgnoreCase(category);
    }

    /**
     * Checks if this spec is an Auth/Relations spec.
     */
    public boolean isAuthRelations() {
        return "AUTH_RELATIONS".equalsIgnoreCase(category) || "AUTH".equalsIgnoreCase(category);
    }

    /**
     * Checks if this spec is a Business Rules spec.
     */
    public boolean isBusinessRules() {
        return "BUSINESS_RULES".equalsIgnoreCase(category) || "BUSINESS".equalsIgnoreCase(category);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoldenSpec that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}