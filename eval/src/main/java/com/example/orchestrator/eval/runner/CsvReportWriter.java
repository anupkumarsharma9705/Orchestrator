package com.example.orchestrator.eval.runner;

import com.example.orchestrator.eval.model.EvaluationResult;
import com.example.orchestrator.eval.model.EvaluationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Writes evaluation results to CSV format.
 */
@Component
public class CsvReportWriter {

    private static final Logger log = LoggerFactory.getLogger(CsvReportWriter.class);

    private static final String[] HEADERS = {
        "run_id", "run_name", "spec_id", "spec_category", "job_id",
        "success", "duration_seconds", "build_ok", "tests_passed", "tests_failed",
        "test_pass_rate", "coverage_percent", "findings_count", "high_critical_findings",
        "cost_usd", "rounds_completed", "entities_generated", "endpoints_generated",
        "meets_quality_bar", "error_message", "started_at", "finished_at"
    };

    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;

    /**
     * Writes evaluation summary to CSV file.
     * 
     * @param summary Evaluation summary to write
     * @param outputPath Path to output CSV file
     * @throws IOException If write fails
     */
    public void write(EvaluationSummary summary, Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write(String.join(",", HEADERS));
            writer.newLine();

            // Write each result
            for (EvaluationResult result : summary.results()) {
                writer.write(formatRow(summary.runId(), summary.runName(), result));
                writer.newLine();
            }
        }

        log.info("Wrote CSV report to {} ({} rows)", outputPath, summary.results().size());
    }

    private String formatRow(String runId, String runName, EvaluationResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(escape(runId)).append(",");
        sb.append(escape(runName)).append(",");
        sb.append(escape(r.specId())).append(",");
        sb.append(escape(r.metadata().getOrDefault("category", "").toString())).append(",");
        sb.append(escape(r.jobId())).append(",");
        sb.append(r.success()).append(",");
        sb.append(r.duration().getSeconds()).append(",");
        sb.append(r.buildOk()).append(",");
        sb.append(r.testsPassed()).append(",");
        sb.append(r.testsFailed()).append(",");
        sb.append(String.format("%.2f", r.getTestPassRate() * 100)).append(",");
        sb.append(r.coveragePercent().toPlainString()).append(",");
        sb.append(r.findingsCount()).append(",");
        sb.append(r.highCriticalFindings()).append(",");
        sb.append(r.costUsd().toPlainString()).append(",");
        sb.append(r.roundsCompleted()).append(",");
        sb.append(escape(String.join(";", r.entitiesGenerated()))).append(",");
        sb.append(escape(String.join(";", r.endpointsGenerated()))).append(",");
        sb.append(r.meetsQualityBar()).append(",");
        sb.append(escape(r.errorMessage())).append(",");
        sb.append(r.startedAt().toString()).append(",");
        sb.append(r.finishedAt().toString());
        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}