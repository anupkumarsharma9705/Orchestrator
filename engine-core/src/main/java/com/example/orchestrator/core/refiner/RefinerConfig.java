package com.example.orchestrator.core.refiner;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Refiner.
 * Fixed stack and rules come from config (not the model).
 */
@ConfigurationProperties(prefix = "orchestrator.refiner")
public class RefinerConfig {

    private String fixedStack = "Java 21, Spring Boot 3.4, Maven, PostgreSQL, JWT";
    
    private String securityRules = "JWT auth, BCrypt passwords, parameterized queries, CORS allow-list, rate limiting on auth, Actuator health only public";
    
    private String validationRules = "@Valid on DTOs, ISBN format validation, @Size on strings, @NotNull on required fields";
    
    private String testingRules = "JUnit 5, Testcontainers, 80% coverage, unit tests for services, integration tests for controllers";
    
    private String conventions = "Constructor injection, records for DTOs, MapStruct for mapping, global exception handler";
    
    private String outOfScope = "GraphQL, WebSocket, Admin UI, OAuth2, Multi-tenancy";
    
    private String acceptanceCriteria = "mvn verify passes, 0 HIGH/CRITICAL findings, tests pass, coverage >= 80%";

    public String getFixedStack() { return fixedStack; }
    public void setFixedStack(String fixedStack) { this.fixedStack = fixedStack; }

    public String getSecurityRules() { return securityRules; }
    public void setSecurityRules(String securityRules) { this.securityRules = securityRules; }

    public String getValidationRules() { return validationRules; }
    public void setValidationRules(String validationRules) { this.validationRules = validationRules; }

    public String getTestingRules() { return testingRules; }
    public void setTestingRules(String testingRules) { this.testingRules = testingRules; }

    public String getConventions() { return conventions; }
    public void setConventions(String conventions) { this.conventions = conventions; }

    public String getOutOfScope() { return outOfScope; }
    public void setOutOfScope(String outOfScope) { this.outOfScope = outOfScope; }

    public String getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(String acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }
}
