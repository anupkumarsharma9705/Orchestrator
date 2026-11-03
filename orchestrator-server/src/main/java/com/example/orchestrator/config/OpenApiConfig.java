package com.example.orchestrator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger Configuration (LV-06).
 * Configures API documentation with Bearer token authentication.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .servers(List.of(
                new Server()
                    .url("https://localhost:" + serverPort + contextPath)
                    .description("Local Development Server"),
                new Server()
                    .url("https://api.example.com")
                    .description("Production Server")
            ))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT Authorization header using the Bearer scheme. Example: \"Authorization: Bearer {token}\"")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .info(new Info()
                .title("Java Companion Orchestrator API")
                .version("0.0.1-SNAPSHOT")
                .description("""
                    REST API for the Java Companion Orchestrator - a CLI tool that generates
                    production-ready Spring Boot REST API projects from natural language descriptions,
                    with built-in security scanning, testing, and auto-fixing.
                    
                    ## Authentication
                    All endpoints require JWT Bearer token authentication.
                    Include the token in the Authorization header: `Authorization: Bearer <token>`
                    
                    ## Rate Limiting
                    API endpoints are rate-limited per the Resilience4j configuration.
                    See `/actuator/metrics/resilience4j.ratelimiter.available.permissions` for current limits.
                    
                    ## Error Handling
                    All errors follow a consistent format with HTTP status codes:
                    - 400: Bad Request (validation errors)
                    - 401: Unauthorized (missing/invalid token)
                    - 403: Forbidden (insufficient permissions)
                    - 404: Not Found
                    - 409: Conflict
                    - 500: Internal Server Error
                    - 503: Service Unavailable (provider unavailable)
                    """)
                .contact(new Contact()
                    .name("Java Companion Orchestrator Team")
                    .url("https://github.com/example/java-companion")
                    .email("support@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.html"))
            );
    }
}