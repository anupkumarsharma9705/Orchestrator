package com.example.orchestrator.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Generates OpenAPI spec file at application startup.
 * 
 * H-25: OpenAPI Spec Generation - saves the generated OpenAPI spec to a file
 * at application startup. The spec is available at runtime via /api-docs endpoint
 * and also saved as a static file for CI/CD and documentation purposes.
 */
@Configuration
public class OpenApiSpecGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenApiSpecGenerator.class);

    private final OpenAPI openAPI;
    private final ResourceLoader resourceLoader;

    @Value("${springdoc.api-docs.path:/api-docs}")
    private String apiDocsPath;

    @Value("${springdoc.output.dir:build/openapi}")
    private String outputDir;

    @Value("${springdoc.output.filename:openapi.json}")
    private String outputFileName;

    public OpenApiSpecGenerator(OpenAPI openAPI, ResourceLoader resourceLoader) {
        this.openAPI = openAPI;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Only generate in non-test environments
        if (isTestEnvironment()) {
            log.info("Skipping OpenAPI spec generation in test environment");
            return;
        }

        try {
            File outputDirectory = new File(outputDir);
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            File outputFile = new File(outputDirectory, outputFileName);
            
            // Generate JSON spec
            String jsonSpec = generateJsonSpec();
            
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                writer.write(jsonSpec);
            }

            log.info("OpenAPI spec generated at: {}", outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            log.warn("Failed to generate OpenAPI spec file: {}", e.getMessage());
            // Don't fail startup if spec generation fails
        }
    }

    private boolean isTestEnvironment() {
        // Check if we're running in test profile
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        return activeProfiles.contains("test") ||
               (envProfiles != null && envProfiles.contains("test"));
    }

    private String generateJsonSpec() throws IOException {
        // Use Jackson to serialize the OpenAPI object to JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAPI);
    }
}