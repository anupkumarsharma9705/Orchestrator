# Project

## Overview
AI-assisted Software Engineering Control Plane - A modular Spring Boot application that orchestrates AI-driven code generation, review, testing, and verification with human-in-the-loop approval gates.

## Project Structure
```
orchestrator/
├── src/main/java/com/example/orchestrator/
│   ├── api/                    # REST API controllers and DTOs
│   ├── approval/               # Approval service and decisions
│   ├── artifact/               # Artifact persistence
│   ├── audit/                  # Audit logging
│   ├── context/                # Context selection and management
│   ├── execution/              # Task orchestration and state machine
│   ├── memory/                 # Project memory management
│   ├── permission/             # Permission enforcement
│   ├── project/                # Project management
│   ├── repair/                 # Automated repair loop
│   ├── requirement/            # Requirement compilation
│   ├── task/                   # Task planning
│   ├── tool/                   # Tool registry and implementations
│   ├── verification/           # Verification pipeline
│   ├── config/                 # Configuration classes
│   └── OrchestratorApplication.java
├── src/test/                   # Unit and integration tests
├── src/main/resources/
│   ├── application.yml         # Main configuration
│   └── application-test.yml    # Test profile config
├── pom.xml                     # Maven build
└── .ai/                        # Project memory (created at runtime)
```

## Technology Stack
- **Language**: Java 21
- **Framework**: Spring Boot 4.1.1
- **AI**: Spring AI 2.0.1 (OpenRouter)
- **Build**: Maven 3.9+
- **Testing**: JUnit 5, Mockito
- **Serialization**: Jackson (with JavaTimeModule)
- **Future**: PostgreSQL, Redis, Docker

## Configuration
Key configuration in `application.yml`:
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENROUTER_API_KEY}
      base-url: https://openrouter.ai/api/v1
      chat:
        options:
          model: poolside/laguna-s-2.1:free

orchestrator:
  pipeline:
    # Project-specific settings (configured per project)
```

## Project Registration
Projects are registered via REST API:
```bash
POST /api/projects
{
  "id": "my-project",
  "name": "My Project",
  "rootPath": "/path/to/project",
  "buildTool": "maven",
  "testCommand": "test"
}
```

## Running
```bash
# Build
mvn clean verify

# Run
mvn spring-boot:run

# Or with Docker (future)
docker compose up
```

## Testing
```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=PermissionManagerTest
```

## Environment Variables
- `OPENROUTER_API_KEY` - Required for AI model access
- `SERVER_PORT` - Server port (default 8080)