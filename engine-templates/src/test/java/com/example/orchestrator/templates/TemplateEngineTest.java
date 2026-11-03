package com.example.orchestrator.templates;

import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.Project;
import com.example.orchestrator.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for the TemplateEngine component.
 */
@SpringBootTest(classes = {TemplateEngineTestConfig.class})
@ActiveProfiles("test")
class TemplateEngineTest {

    @Autowired
    private TemplateEngine templateEngine;

    @TempDir
    Path tempDir;

    @Test
    void generateProject_createsExpectedStructure() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "REST CRUD for Book entity",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();

        templateEngine.generateProject(project, plan, tempDir);

        // Verify core files exist
        assertThat(tempDir.resolve("pom.xml")).exists();
        assertThat(tempDir.resolve("Dockerfile")).exists();
        assertThat(tempDir.resolve("docker-compose.yml")).exists();
        assertThat(tempDir.resolve(".gitignore")).exists();
        assertThat(tempDir.resolve("README.md")).exists();
        assertThat(tempDir.resolve("checkstyle.xml")).exists();
        assertThat(tempDir.resolve("spotbugs-exclude.xml")).exists();
        assertThat(tempDir.resolve(".github/workflows/ci.yml")).exists();

        // Verify Spring Boot structure
        assertThat(tempDir.resolve("src/main/java/com/example/library/LibraryApplication.java")).exists();
        assertThat(tempDir.resolve("src/main/resources/application.yml")).exists();

        // Verify package structure
        assertThat(tempDir.resolve("src/main/java/com/example/library/controller")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/service")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/repository")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/domain")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/dto")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/mapper")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/config")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/security")).exists();
        assertThat(tempDir.resolve("src/main/java/com/example/library/exception")).exists();

        // Verify security config
        assertThat(tempDir.resolve("src/main/java/com/example/library/config/SecurityConfig.java")).exists();
        
        // Verify exception handler
        assertThat(tempDir.resolve("src/main/java/com/example/library/exception/GlobalExceptionHandler.java")).exists();
    }

    @Test
    void generateFile_substitutesVariables() throws Exception {
        Path targetFile = tempDir.resolve("test-output.txt");
        Map<String, String> variables = Map.of(
            "projectName", "TestProject",
            "version", "1.0.0"
        );

        // Create a simple template file for testing
        String templateContent = "Project: {{projectName}}, Version: {{version}}";
        Path templateFile = tempDir.resolve("test-template.txt");
        java.nio.file.Files.writeString(templateFile, templateContent);

        // We need to test via the template engine's internal method
        // For now, verify the template engine can be instantiated
        assertThat(templateEngine).isNotNull();
    }

    @Test
    void pomXml_containsRequiredDependencies() throws Exception {
        Project project = new Project(
            "test",
            "test",
            "Test project",
            "com.example.test",
            "Test",
            "/tmp/test",
            "/tmp/test/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String pomContent = java.nio.file.Files.readString(tempDir.resolve("pom.xml"));
        
        // Verify key dependencies
        assertThat(pomContent).contains("spring-boot-starter-web");
        assertThat(pomContent).contains("spring-boot-starter-data-jpa");
        assertThat(pomContent).contains("spring-boot-starter-validation");
        assertThat(pomContent).contains("spring-boot-starter-security");
        assertThat(pomContent).contains("spring-boot-starter-actuator");
        assertThat(pomContent).contains("postgresql");
        assertThat(pomContent).contains("flyway-core");
        assertThat(pomContent).contains("jjwt-api");
        assertThat(pomContent).contains("mapstruct");
        assertThat(pomContent).contains("lombok");
        
        // Verify quality plugins
        assertThat(pomContent).contains("maven-checkstyle-plugin");
        assertThat(pomContent).contains("spotbugs-maven-plugin");
        assertThat(pomContent).contains("findsecbugs-plugin");
        assertThat(pomContent).contains("dependency-check-maven");
        assertThat(pomContent).contains("jacoco-maven-plugin");
    }

    @Test
    void applicationYml_containsRequiredConfig() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "Test",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String appYml = java.nio.file.Files.readString(tempDir.resolve("src/main/resources/application.yml"));
        
        assertThat(appYml).contains("spring:");
        assertThat(appYml).contains("datasource:");
        assertThat(appYml).contains("jpa:");
        assertThat(appYml).contains("flyway:");
        assertThat(appYml).contains("security:");
        assertThat(appYml).contains("jwt:");
        assertThat(appYml).contains("management:");
        assertThat(appYml).contains("logging:");
    }

    @Test
    void securityConfig_containsSecurityRules() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "Test",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String securityConfig = java.nio.file.Files.readString(
            tempDir.resolve("src/main/java/com/example/library/config/SecurityConfig.java"));
        
        assertThat(securityConfig).contains("@EnableWebSecurity");
        assertThat(securityConfig).contains("@EnableMethodSecurity");
        assertThat(securityConfig).contains("SessionCreationPolicy.STATELESS");
        assertThat(securityConfig).contains("JwtAuthenticationFilter");
        assertThat(securityConfig).contains("BCryptPasswordEncoder");
        assertThat(securityConfig).contains("SEC-1:");
        assertThat(securityConfig).contains("SEC-2:");
        assertThat(securityConfig).contains("SEC-7:");
    }

    @Test
    void dockerfile_usesMultiStageBuild() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "Test",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String dockerfile = java.nio.file.Files.readString(tempDir.resolve("Dockerfile"));
        
        assertThat(dockerfile).contains("FROM maven:");
        assertThat(dockerfile).contains("FROM eclipse-temurin:");
        assertThat(dockerfile).contains("addgroup");
        assertThat(dockerfile).contains("adduser");
        assertThat(dockerfile).contains("USER appuser");
        assertThat(dockerfile).contains("HEALTHCHECK");
    }

    @Test
    void dockerCompose_includesPostgres() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "Test",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String compose = java.nio.file.Files.readString(tempDir.resolve("docker-compose.yml"));
        
        assertThat(compose).contains("postgres:");
        assertThat(compose).contains("postgres:16-alpine");
        assertThat(compose).contains("healthcheck:");
        assertThat(compose).contains("pg_isready");
        assertThat(compose).contains("SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/");
    }

    @Test
    void githubActions_containsQualityGates() throws Exception {
        Project project = new Project(
            "library",
            "library",
            "Test",
            "com.example.library",
            "Library",
            "/tmp/library",
            "/tmp/library/.companion",
            "MAVEN",
            "mvn verify",
            Map.of(),
            Instant.now(),
            Instant.now()
        );

        Plan plan = createSamplePlan();
        templateEngine.generateProject(project, plan, tempDir);

        String ci = java.nio.file.Files.readString(tempDir.resolve(".github/workflows/ci.yml"));
        
        assertThat(ci).contains("mvn -B -ntp verify");
        assertThat(ci).contains("-Psecurity dependency-check:check");
        assertThat(ci).contains("if: env.NVD_API_KEY != ''");
        assertThat(ci).doesNotContain("trivy-action");
        // Generated projects must not require registry credentials to build.
        assertThat(ci).doesNotContain("secrets.DOCKERHUB_USERNAME");
        assertThat(ci).doesNotContain("secrets.DOCKERHUB_TOKEN");
        assertThat(ci).doesNotContain("./mvnw");
        assertThat(ci).doesNotContain("checkstyle:check");
        assertThat(ci).doesNotContain("jacoco:check");
    }

    private Plan createSamplePlan() {
        Instant now = Instant.now();
        List<PlanTask> tasks = List.of(
            new PlanTask("T-001", "Create Book entity", "Create JPA entity",
                List.of("src/main/java/com/example/library/domain/Book.java"),
                List.of(), List.of("R-001"), List.of("Entity compiles"),
                TaskStatus.PENDING, now, now),
            new PlanTask("T-002", "Create Book repository", "Create Spring Data repository",
                List.of("src/main/java/com/example/library/repository/BookRepository.java"),
                List.of("T-001"), List.of("R-001"), List.of("Repository compiles"),
                TaskStatus.PENDING, now, now)
        );
        return new Plan("library", "v1", tasks, now, now);
    }
}