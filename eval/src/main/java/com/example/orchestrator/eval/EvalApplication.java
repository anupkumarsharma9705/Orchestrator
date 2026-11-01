package com.example.orchestrator.eval;

import com.example.orchestrator.eval.model.EvaluationSummary;
import com.example.orchestrator.eval.runner.CsvReportWriter;
import com.example.orchestrator.eval.runner.EvaluationRunner;
import com.example.orchestrator.eval.runner.SpecLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Evaluation Suite CLI Application.
 * 
 * Usage:
 *   java -jar eval.jar [--specs-dir=DIR] [--output-dir=DIR] [--specs=SPEC1,SPEC2] [--max-parallel=N]
 * 
 * Examples:
 *   java -jar eval.jar --specs-dir=eval/specs --output-dir=target/eval-reports
 *   java -jar eval.jar --specs=CRUD-001,AUTH-001 --max-parallel=1
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.example.orchestrator")
public class EvalApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalApplication.class);

    private final SpecLoader specLoader;
    private final EvaluationRunner evaluationRunner;
    private final CsvReportWriter csvReportWriter;

    public EvalApplication(SpecLoader specLoader,
                           EvaluationRunner evaluationRunner,
                           CsvReportWriter csvReportWriter) {
        this.specLoader = specLoader;
        this.evaluationRunner = evaluationRunner;
        this.csvReportWriter = csvReportWriter;
    }

    public static void main(String[] args) {
        SpringApplication.run(EvalApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Parse command line arguments
        String specsDir = "eval/specs";
        String outputDir = "target/eval-reports";
        List<String> specIds = List.of();
        int maxParallel = 2;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--specs-dir" -> specsDir = args[++i];
                case "--output-dir" -> outputDir = args[++i];
                case "--specs" -> specIds = List.of(args[++i].split(","));
                case "--max-parallel" -> maxParallel = Integer.parseInt(args[++i]);
            }
        }

        log.info("Starting Evaluation Suite");
        log.info("Specs directory: {}", specsDir);
        log.info("Output directory: {}", outputDir);
        log.info("Max parallel: {}", maxParallel);

        // Load specs
        List<com.example.orchestrator.eval.model.GoldenSpec> specs;
        if (specIds.isEmpty()) {
            // Load all from directory
            specs = specLoader.loadAll();
        } else {
            // Load specific specs
            specs = specIds.stream()
                .map(id -> {
                    try {
                        return specLoader.loadSpecById(id).orElseThrow(
                            () -> new IllegalArgumentException("Spec not found: " + id));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load spec: " + id, e);
                    }
                })
                .toList();
        }

        if (specs.isEmpty()) {
            log.warn("No specs found to run");
            return;
        }

        log.info("Loaded {} specs: {}", specs.size(), 
            specs.stream().map(com.example.orchestrator.eval.model.GoldenSpec::id).toList());

        // Run evaluation
        String runName = "eval-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        com.example.orchestrator.eval.model.EvaluationSummary summary = 
            evaluationRunner.run(specs, runName);

        // Print summary to console
        printSummary(summary);

        // Write CSV report
        Path outputPath = Paths.get(outputDir, "eval-" + 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
        csvReportWriter.write(summary, Paths.get(outputDir, "eval-results.csv"));
        
        log.info("Evaluation complete. Success rate: {:.1f}%, Quality rate: {:.1f}%",
            summary.getSuccessRate() * 100, summary.getQualityRate() * 100);
    }

    private void printSummary(com.example.orchestrator.eval.model.EvaluationSummary summary) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    EVALUATION SUMMARY                         ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Run: %-53s ║\n", summary.runName());
        System.out.printf("║ Total Specs: %-47d ║\n", summary.totalSpecs());
        System.out.printf("║ Passed: %-52d ║\n", summary.passedSpecs());
        System.out.printf("║ Failed: %-52d ║\n", summary.failedSpecs());
        System.out.printf("║ Success Rate: %-46.1f%% ║\n", summary.getSuccessRate() * 100);
        System.out.printf("║ Quality Rate: %-46.1f%% ║\n", summary.getQualityRate() * 100);
        System.out.printf("║ Total Cost: $%-46s ║\n", summary.totalCostUsd().toPlainString());
        System.out.printf("║ Avg Coverage: %-46.1f%% ║\n", summary.avgCoveragePercent());
        System.out.printf("║ Avg Test Pass Rate: %-40.1f%% ║\n", summary.avgTestPassRate() * 100);
        System.out.printf("║ High/Critical Findings: %-38d ║\n", summary.totalHighCriticalFindings());
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // Per-spec results
        System.out.println("Per-spec results:");
        System.out.printf("%-12s %-10s %-8s %-8s %-8s %-8s %-8s %-10s\n",
            "SPEC", "CATEGORY", "SUCCESS", "BUILD", "TESTS", "COV%", "HIGH/CRIT", "COST");
        System.out.println("─".repeat(80));
        
        for (var result : summary.results()) {
            String category = (String) result.metadata().getOrDefault("category", "UNKNOWN");
            System.out.printf("%-12s %-10s %-8s %-8s %d/%d    %-5.1f    %-8d    $%-6s\n",
                result.specId(),
                category,
                result.success() ? "✓" : "✗",
                result.buildOk() ? "✓" : "✗",
                result.testsPassed(), result.testsPassed() + result.testsFailed(),
                result.coveragePercent(),
                result.highCriticalFindings(),
                result.costUsd().toPlainString()
            );
        }
    }
}