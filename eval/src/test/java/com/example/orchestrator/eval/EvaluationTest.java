package com.example.orchestrator.eval;

import com.example.orchestrator.eval.model.EvaluationResult;
import com.example.orchestrator.eval.model.EvaluationSummary;
import com.example.orchestrator.eval.model.GoldenSpec;
import com.example.orchestrator.eval.runner.CsvReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EvaluationTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSpecFromYaml() throws IOException {
        // Create a test spec file
        Path specFile = tempDir.resolve("TEST-001.yaml");
        String yaml = """
            id: "TEST-001"
            category: "CRUD"
            name: "Test Spec"
            description: "A test specification"
            expectedEntities: ["Entity1"]
            expectedEndpoints: ["GET /api/test"]
            expectedSecurity: ["JWT"]
            expectedTests: ["UNIT"]
            fixedAnswers: "[]"
            timeoutSec: 300
            createdAt: "2026-10-28T00:00:00Z"
            """;
        Files.writeString(specFile, yaml);

        // Verify the YAML parsing works - register JSR310 module for Instant
        com.fasterxml.jackson.dataformat.yaml.YAMLFactory yamlFactory = 
            new com.fasterxml.jackson.dataformat.yaml.YAMLFactory();
        com.fasterxml.jackson.databind.ObjectMapper mapper = 
            new com.fasterxml.jackson.databind.ObjectMapper(yamlFactory);
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        GoldenSpec spec = mapper.readValue(yaml, GoldenSpec.class);
        assertThat(spec.id()).isEqualTo("TEST-001");
        assertThat(spec.category()).isEqualTo("CRUD");
        assertThat(spec.expectedEntities()).containsExactly("Entity1");
    }

    @Test
    void evaluationResultQualityBar() {
        Instant now = Instant.now();
        EvaluationResult passing = new EvaluationResult(
            "TEST-001", "job-1", true, Duration.ofSeconds(60),
            true, 10, 0, BigDecimal.valueOf(85), 2, 0,
            BigDecimal.valueOf(0.01), 3,
            List.of("Entity1"), List.of("GET /api/test"),
            null, now.minusSeconds(60), now,
            Map.of("category", "CRUD")
        );

        EvaluationResult failing = new EvaluationResult(
            "TEST-002", "job-2", true, Duration.ofSeconds(60),
            true, 5, 2, BigDecimal.valueOf(70), 5, 2,
            BigDecimal.valueOf(0.02), 5,
            List.of("Entity1"), List.of("GET /api/test"),
            null, now.minusSeconds(60), now,
            Map.of("category", "AUTH")
        );

        EvaluationResult error = new EvaluationResult(
            "TEST-003", "job-3", false, Duration.ofSeconds(10),
            false, 0, 0, BigDecimal.ZERO, 0, 0,
            BigDecimal.ZERO, 0,
            List.of(), List.of(),
            "Compilation failed", now.minusSeconds(10), now,
            Map.of("category", "BUSINESS")
        );

        assertThat(passing.meetsQualityBar()).isTrue();
        assertThat(failing.meetsQualityBar()).isFalse();
        assertThat(error.meetsQualityBar()).isFalse();
        assertThat(passing.getTestPassRate()).isEqualTo(1.0);
        assertThat(failing.getTestPassRate()).isEqualTo(5.0 / 7.0);
    }

    @Test
    void evaluationSummaryCalculations() {
        Instant now = Instant.now();
        EvaluationResult r1 = new EvaluationResult(
            "CRUD-001", "job-1", true, Duration.ofSeconds(60),
            true, 10, 0, BigDecimal.valueOf(85), 2, 0,
            BigDecimal.valueOf(0.01), 3,
            List.of("Book"), List.of("GET /api/books"),
            null, now.minusSeconds(60), now,
            Map.of("category", "CRUD")
        );

        EvaluationResult r2 = new EvaluationResult(
            "AUTH-001", "job-2", true, Duration.ofSeconds(90),
            true, 15, 0, BigDecimal.valueOf(90), 1, 0,
            BigDecimal.valueOf(0.02), 4,
            List.of("User", "Post"), List.of("GET /api/posts"),
            null, now.minusSeconds(90), now,
            Map.of("category", "AUTH")
        );

        EvaluationResult r3 = new EvaluationResult(
            "BIZ-001", "job-3", false, Duration.ofSeconds(120),
            false, 0, 0, BigDecimal.ZERO, 0, 0,
            BigDecimal.ZERO, 0,
            List.of(), List.of(),
            "Timeout", now.minusSeconds(120), now,
            Map.of("category", "BUSINESS")
        );

        EvaluationSummary summary = new EvaluationSummary(
            "run-1", "Test Run", 3, 2, 1, 0,
            Duration.ofSeconds(270), BigDecimal.valueOf(0.03),
            87.5, 0.83, 0, 2,
            List.of(r1, r2, r3), now.minusSeconds(270), now,
            Map.of()
        );

        assertThat(summary.totalSpecs()).isEqualTo(3);
        assertThat(summary.passedSpecs()).isEqualTo(2);
        assertThat(summary.failedSpecs()).isEqualTo(1);
        assertThat(summary.getSuccessRate()).isEqualTo(2.0 / 3.0);
        assertThat(summary.getQualityRate()).isEqualTo(2.0 / 3.0);
        assertThat(summary.totalCostUsd()).isEqualByComparingTo(BigDecimal.valueOf(0.03));
    }

    @Test
    void csvReportWriter(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.now();
        EvaluationResult result = new EvaluationResult(
            "CRUD-001", "job-1", true, Duration.ofSeconds(60),
            true, 10, 0, BigDecimal.valueOf(85), 2, 0,
            BigDecimal.valueOf(0.01), 3,
            List.of("Book"), List.of("GET /api/books"),
            null, now.minusSeconds(60), now,
            Map.of("category", "CRUD")
        );

        EvaluationSummary summary = new EvaluationSummary(
            "run-1", "Test Run", 1, 1, 0, 0,
            Duration.ofSeconds(60), BigDecimal.valueOf(0.01),
            85.0, 1.0, 0, 1,
            List.of(result), now.minusSeconds(60), now,
            Map.of()
        );

        Path output = tempDir.resolve("test-report.csv");
        CsvReportWriter writer = new CsvReportWriter();
        writer.write(summary, output);

        assertThat(Files.exists(output)).isTrue();
        String content = Files.readString(output);
        assertThat(content).contains("CRUD-001");
        assertThat(content).contains("CRUD");
        assertThat(content).contains("true");
        assertThat(content).contains("85");
    }
}