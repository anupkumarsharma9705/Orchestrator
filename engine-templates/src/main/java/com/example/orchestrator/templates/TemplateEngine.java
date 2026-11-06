package com.example.orchestrator.templates;

import com.example.orchestrator.model.Plan;
import com.example.orchestrator.model.PlanTask;
import com.example.orchestrator.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template engine for generating Spring Boot project skeleton.
 * Loads templates from classpath, processes variables, and writes to project workspace.
 */
@Service
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    private static final String TEMPLATE_BASE = "template/";
    private static final String SPRING_BOOT_VERSION = "4.1.1";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    private final Map<String, String> templateCache = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("TemplateEngine initialized with base: {}", TEMPLATE_BASE);
        loadAllTemplates();
    }

    /**
     * Loads all template files from classpath.
     */
    private void loadAllTemplates() {
        try {
            // We'll load templates on-demand instead of pre-loading
            log.debug("Template loading configured for on-demand");
        } catch (Exception e) {
            log.warn("Failed to pre-load templates: {}", e.getMessage());
        }
    }

    /**
     * Generates a complete Spring Boot project skeleton in the target directory.
     * 
     * @param project The project configuration
     * @param plan The execution plan with tasks
     * @param targetDir Target directory for generated project
     * @throws IOException If template processing or file writing fails
     */
    public void generateProject(Project project, Plan plan, Path targetDir) throws IOException {
        log.info("Generating Spring Boot project '{}' in {}", project.name(), targetDir);

        // Create directory structure
        createDirectoryStructure(targetDir);

        // Generate core files
        generatePomXml(project, targetDir);
        generateApplicationClass(project, targetDir);
        generateApplicationProperties(project, targetDir);
        generateDockerfile(project, targetDir);
        generateDockerCompose(project, targetDir);
        generateGitignore(targetDir);
        generateReadme(project, targetDir);

        // Generate Spring Boot package structure
        generatePackageStructure(project, targetDir);

        // Generate security configuration
        generateSecurityConfig(project, targetDir);
        generateJwtAuthenticationFilter(project, targetDir);

        // Generate global exception handler
        generateExceptionHandler(project, targetDir);

        // Generate GitHub Actions CI
        generateGitHubActions(project, targetDir);

        // Generate Checkstyle config
        generateCheckstyleConfig(targetDir);

        // Generate SpotBugs config
        generateSpotBugsConfig(targetDir);

        // Generate JaCoCo config (in pom.xml)

        log.info("Project skeleton generated successfully for: {}", project.name());
    }

    /**
     * Generates a single file from template with variable substitution.
     */
    public void generateFile(String templatePath, Path targetFile, Map<String, String> variables) throws IOException {
        String template = loadTemplate(templatePath);
        String content = substituteVariables(template, variables);
        
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        
        log.debug("Generated file: {}", targetFile);
    }

    /**
     * Creates standard Spring Boot directory structure.
     */
    private void createDirectoryStructure(Path targetDir) throws IOException {
        String[] dirs = {
            "src/main/java",
            "src/main/resources",
            "src/test/java",
            "src/test/resources",
            ".github/workflows"
        };

        for (String dir : dirs) {
            Files.createDirectories(targetDir.resolve(dir));
        }
    }

    /**
     * Generates pom.xml with all required dependencies and plugins.
     */
    private void generatePomXml(Project project, Path targetDir) throws IOException {
        Map<String, String> vars = createCommonVariables(project);
        vars.put("project.description", "Spring Boot REST API for " + project.name());
        vars.put("java.version", "21");
        vars.put("spring.boot.version", SPRING_BOOT_VERSION);
        vars.put("maven.compiler.source", "21");
        vars.put("maven.compiler.target", "21");

        generateFile("pom.xml", targetDir.resolve("pom.xml"), vars);
    }

    /**
     * Generates main application class.
     */
    private void generateApplicationClass(Project project, Path targetDir) throws IOException {
        String packagePath = project.basePackage().replace(".", "/");
        Path targetFile = targetDir.resolve("src/main/java/" + packagePath + "/" + project.className() + "Application.java");
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("package", project.basePackage());
        vars.put("className", project.className() + "Application");

        generateFile("Application.java", targetFile, vars);
    }

    /**
     * Generates application.yml with database and security config.
     */
    private void generateApplicationProperties(Project project, Path targetDir) throws IOException {
        Path targetFile = targetDir.resolve("src/main/resources/application.yml");
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("datasource.url", "jdbc:postgresql://localhost:5432/" + project.name().toLowerCase());
        vars.put("datasource.username", "${DB_USERNAME:postgres}");
        vars.put("datasource.password", "${DB_PASSWORD:postgres}");
        vars.put("jpa.ddl-auto", "validate");
        vars.put("jwt.secret", "${JWT_SECRET:}");
        vars.put("jwt.expiration", "${JWT_EXPIRATION_MS:86400000}"); // 24 hours

        generateFile("application.yml", targetFile, vars);
    }

    /**
     * Generates Dockerfile.
     */
    private void generateDockerfile(Project project, Path targetDir) throws IOException {
        Map<String, String> vars = createCommonVariables(project);

        generateFile("Dockerfile", targetDir.resolve("Dockerfile"), vars);
    }

    /**
     * Generates docker-compose.yml.
     */
    private void generateDockerCompose(Project project, Path targetDir) throws IOException {
        Map<String, String> vars = createCommonVariables(project);
        vars.put("serviceName", project.name().toLowerCase());
        vars.put("dbName", project.name().toLowerCase());
        vars.put("dbUser", "postgres");
        vars.put("dbPassword", "postgres");

        generateFile("docker-compose.yml", targetDir.resolve("docker-compose.yml"), vars);
    }

    /**
     * Generates .gitignore.
     */
    private void generateGitignore(Path targetDir) throws IOException {
        generateFile("gitignore", targetDir.resolve(".gitignore"), Map.of());
    }

    /**
     * Generates README.md.
     */
    private void generateReadme(Project project, Path targetDir) throws IOException {
        Map<String, String> vars = createCommonVariables(project);
        generateFile("README.md", targetDir.resolve("README.md"), vars);
    }

    /**
     * Generates Spring Boot package structure with empty packages.
     */
    private void generatePackageStructure(Project project, Path targetDir) throws IOException {
        String basePath = "src/main/java/" + project.basePackage().replace(".", "/");
        
        String[] packages = {
            "controller",
            "service",
            "repository",
            "domain",
            "dto",
            "mapper",
            "config",
            "security",
            "exception"
        };

        for (String pkg : packages) {
            Path pkgDir = targetDir.resolve(basePath + "/" + pkg);
            Files.createDirectories(pkgDir);
            
            // Create package-info.java for documentation
            String pkgInfo = """
                /**
                 * %s package.
                 */
                package %s.%s;
                """.formatted(pkg.substring(0, 1).toUpperCase() + pkg.substring(1), project.basePackage(), pkg);
            
            Files.writeString(pkgDir.resolve("package-info.java"), pkgInfo, StandardCharsets.UTF_8);
        }
    }

    /**
     * Generates SecurityConfig.java.
     */
    private void generateSecurityConfig(Project project, Path targetDir) throws IOException {
        String packagePath = project.basePackage().replace(".", "/");
        Path targetFile = targetDir.resolve("src/main/java/" + packagePath + "/config/SecurityConfig.java");
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("package", project.basePackage() + ".config");

        generateFile("SecurityConfig.java", targetFile, vars);
    }

    /**
     * Generates JwtAuthenticationFilter.java.
     */
    private void generateJwtAuthenticationFilter(Project project, Path targetDir) throws IOException {
        String packagePath = project.basePackage().replace(".", "/");
        Path targetFile = targetDir.resolve("src/main/java/" + packagePath + "/security/JwtAuthenticationFilter.java");
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("package", project.basePackage() + ".security");

        generateFile("JwtAuthenticationFilter.java", targetFile, vars);
    }

    /**
     * Generates GlobalExceptionHandler.java.
     */
    private void generateExceptionHandler(Project project, Path targetDir) throws IOException {
        String packagePath = project.basePackage().replace(".", "/");
        Path targetFile = targetDir.resolve("src/main/java/" + packagePath + "/exception/GlobalExceptionHandler.java");
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("package", project.basePackage() + ".exception");

        generateFile("GlobalExceptionHandler.java", targetFile, vars);
    }

    /**
     * Generates GitHub Actions CI workflow.
     */
    private void generateGitHubActions(Project project, Path targetDir) throws IOException {
        Path workflowDir = targetDir.resolve(".github/workflows");
        Files.createDirectories(workflowDir);
        
        Map<String, String> vars = createCommonVariables(project);
        vars.put("projectName", project.name());

        generateFile("ci.yml", workflowDir.resolve("ci.yml"), vars);
    }

    /**
     * Generates checkstyle.xml.
     */
    private void generateCheckstyleConfig(Path targetDir) throws IOException {
        generateFile("checkstyle.xml", targetDir.resolve("checkstyle.xml"), Map.of());
    }

    /**
     * Generates spotbugs-exclude.xml.
     */
    private void generateSpotBugsConfig(Path targetDir) throws IOException {
        generateFile("spotbugs-exclude.xml", targetDir.resolve("spotbugs-exclude.xml"), Map.of());
    }

    /**
     * Loads a template from classpath.
     */
    private String loadTemplate(String templateName) throws IOException {
        // Check cache first
        if (templateCache.containsKey(templateName)) {
            return templateCache.get(templateName);
        }

        String resourcePath = TEMPLATE_BASE + templateName;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            // Try without template/ prefix
            resource = new ClassPathResource(templateName);
        }

        if (!resource.exists()) {
            throw new IOException("Template not found: " + resourcePath);
        }

        try (InputStream is = resource.getInputStream()) {
            String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            templateCache.put(templateName, content);
            return content;
        }
    }

    /**
     * Substitutes {{variable}} placeholders in template.
     */
    private String substituteVariables(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement = variables.getOrDefault(key, "{{" + key + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Creates common variables for template substitution.
     */
    private Map<String, String> createCommonVariables(Project project) {
        Map<String, String> vars = new HashMap<>();
        vars.put("projectName", project.name());
        vars.put("projectDescription", project.description());
        vars.put("basePackage", project.basePackage());
        vars.put("className", project.className());
        vars.put("artifactId", project.name().toLowerCase().replace(" ", "-"));
        vars.put("serviceName", project.name().toLowerCase());
        vars.put("groupId", "com.example");
        vars.put("version", "0.0.1-SNAPSHOT");
        vars.put("spring.boot.version", SPRING_BOOT_VERSION);
        vars.put("year", String.valueOf(java.time.Year.now().getValue()));
        return vars;
    }
}