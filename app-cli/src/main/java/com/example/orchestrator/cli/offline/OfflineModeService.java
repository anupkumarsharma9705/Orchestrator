package com.example.orchestrator.cli.offline;

import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.Project;
import com.example.orchestrator.model.TaskStatus;
import com.example.orchestrator.templates.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating Spring Boot projects offline (without server).
 * Uses the local TemplateEngine to generate a complete Spring Boot project skeleton.
 */
@Service
public class OfflineModeService {

    private static final Logger log = LoggerFactory.getLogger(OfflineModeService.class);

    private final TemplateEngine templateEngine;

    public OfflineModeService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Generates a complete Spring Boot project offline.
     * 
     * @param name Project name
     * @param description Natural language description
     * @param projectRoot Root directory for the project
     * @param basePackage Base Java package (optional, auto-generated if not provided)
     * @param dryRun If true, only shows what would be generated without creating files
     * @return OfflineGenerationResult with details
     */
    public OfflineGenerationResult generateOffline(
            String name,
            String description,
            Path projectRoot,
            String basePackage,
            boolean dryRun) throws IOException {

        log.info("Starting offline generation for project: {}", name);

        // Create project configuration
        String projectName = name.trim();
        String projectDescription = description != null ? description.trim() : "Generated Spring Boot project";
        
        // Derive base package and class name from name if not provided
        String derivedBasePackage = basePackage != null && !basePackage.isBlank() 
            ? basePackage.trim() 
            : "com.example." + projectName.toLowerCase().replace(" ", "").replace("-", "");
        
        String className = toPascalCase(projectName.replace(" ", "").replace("-", ""));
        
        // Create project record
        Project project = new Project(
            UUID.randomUUID().toString(),
            projectName,
            projectDescription,
            derivedBasePackage,
            className,
            projectRoot.toAbsolutePath().toString(),
            projectRoot.resolve(".orchestrator").toAbsolutePath().toString(),
            "MAVEN",
            "test",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        // Create a simple plan for reference
        Plan plan = createBasicPlan(projectName, description);

        // Target directory
        Path targetDir = projectRoot.resolve(projectName).toAbsolutePath().normalize();

        if (dryRun) {
            log.info("DRY RUN: Would generate project '{}' at {}", projectName, targetDir);
            return new OfflineGenerationResult(
                true,
                "DRY RUN: Project structure preview",
                targetDir,
                listFilesThatWouldBeGenerated(project),
                plan
            );
        }

        // Generate the project
        templateEngine.generateProject(project, plan, targetDir);

        log.info("Offline generation completed for: {}", projectName);

        return new OfflineGenerationResult(
            true,
            "Project generated successfully at: " + targetDir,
            targetDir,
            listGeneratedFiles(targetDir),
            plan
        );
    }

    /**
     * Creates a basic execution plan for offline generation.
     */
    private Plan createBasicPlan(String projectName, String description) {
        // Create a simple plan with basic tasks
        List<PlanTask> tasks = List.of(
            new PlanTask(
                "task-1",
                "Project Setup",
                "Create project directory structure and core files (pom.xml, Application.java, application.yml)",
                List.of("pom.xml", "src/main/java/.../Application.java", "src/main/resources/application.yml"),
                List.of(),
                List.of("req-1"),
                List.of("Project structure created", "pom.xml generated", "Application class generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            ),
            new PlanTask(
                "task-2",
                "Security Configuration",
                "Generate Spring Security configuration with JWT authentication",
                List.of("src/main/java/.../config/SecurityConfig.java", "src/main/java/.../security/JwtAuthenticationFilter.java"),
                List.of("task-1"),
                List.of("req-2"),
                List.of("SecurityConfig generated", "JWT filter generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            ),
            new PlanTask(
                "task-3",
                "Exception Handling",
                "Generate global exception handler and error responses",
                List.of("src/main/java/.../exception/GlobalExceptionHandler.java"),
                List.of("task-1"),
                List.of("req-3"),
                List.of("Exception handler generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            ),
            new PlanTask(
                "task-4",
                "CI/CD Pipeline",
                "Generate GitHub Actions workflow for CI/CD",
                List.of(".github/workflows/ci.yml"),
                List.of("task-1"),
                List.of("req-4"),
                List.of("CI workflow generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            ),
            new PlanTask(
                "task-5",
                "Containerization",
                "Generate Dockerfile and docker-compose.yml",
                List.of("Dockerfile", "docker-compose.yml"),
                List.of("task-1"),
                List.of("req-5"),
                List.of("Dockerfile generated", "docker-compose.yml generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            ),
            new PlanTask(
                "task-6",
                "Documentation",
                "Generate README.md and gitignore",
                List.of("README.md", ".gitignore"),
                List.of("task-1"),
                List.of("req-6"),
                List.of("README generated", ".gitignore generated"),
                TaskStatus.PENDING,
                Instant.now(),
                Instant.now()
            )
        );

        return new Plan(
            UUID.randomUUID().toString(),
            "Generate Spring Boot project: " + projectName,
            tasks,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * Lists files that would be generated (for dry run).
     */
    private List<String> listFilesThatWouldBeGenerated(Project project) {
        String basePath = "src/main/java/" + project.basePackage().replace(".", "/");
        String projectName = project.name().toLowerCase().replace(" ", "-");
        
        return List.of(
            "pom.xml",
            "src/main/java/" + project.basePackage().replace(".", "/") + "/" + project.className() + "Application.java",
            "src/main/resources/application.yml",
            "Dockerfile",
            "docker-compose.yml",
            ".gitignore",
            "README.md",
            "Dockerfile",
            "docker-compose.yml",
            ".gitignore",
            "README.md",
            "checkstyle.xml",
            "spotbugs-exclude.xml",
            ".github/workflows/ci.yml",
            "checkstyle.xml",
            "spotbugs-exclude.xml",
            "src/main/java/" + project.basePackage().replace(".", "/") + "/config/SecurityConfig.java",
            "src/main/java/" + project.basePackage().replace(".", "/") + "/security/JwtAuthenticationFilter.java",
            "src/main/java/" + project.basePackage().replace(".", "/") + "/exception/GlobalExceptionHandler.java",
            ".github/workflows/ci.yml",
            "checkstyle.xml",
            "spotbugs-exclude.xml"
        ).stream()
        .map(f -> "  " + f)
        .collect(Collectors.toList());
    }

    /**
     * Lists actually generated files.
     */
    private List<String> listGeneratedFiles(Path targetDir) throws IOException {
        if (!Files.exists(targetDir)) {
            return List.of("  (directory not found)");
        }
        
        return Files.walk(targetDir)
            .filter(Files::isRegularFile)
            .map(targetDir::relativize)
            .map(Path::toString)
            .map(f -> "  " + f)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Converts string to PascalCase.
     */
    private String toPascalCase(String input) {
        String[] parts = input.split("[\\s\\-_]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                      .append(part.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    /**
     * Result of offline generation.
     */
    public record OfflineGenerationResult(
        boolean success,
        String message,
        Path projectPath,
        List<String> files,
        Plan plan
    ) {}
}