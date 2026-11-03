# Conventions

## Code Style

### Java
- **Java Version**: 21
- **Formatting**: Standard Spring Boot conventions
- **Records**: Use for DTOs and immutable data
- **Optional**: Use for nullable return values
- **Streams**: Prefer over loops for collections
- **Annotations**: Use Spring stereotypes (@Service, @Component, @RestController, etc.)

### Package Structure
```
com.example.orchestrator
├── api/           # REST controllers and DTOs (public API)
├── approval/      # Approval workflow
├── artifact/      # Artifact storage
├── audit/         # Audit logging
├── context/       # Context selection
├── execution/     # Task execution engine
├── memory/        # Project memory
├── permission/    # Permission enforcement
├── project/       # Project management
├── repair/        # Automated repair
├── requirement/   # Requirement compilation
├── task/          # Task planning
├── tool/          # Tool registry and implementations
├── verification/  # Verification pipeline
├── config/        # Spring configuration
└── OrchestratorApplication.java
```

### Naming Conventions
- **Classes**: PascalCase (`TaskController`, `ToolRegistry`)
- **Methods**: camelCase (`executePlan`, `getSession`)
- **Fields**: camelCase (`taskId`, `repairId`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_ATTEMPTS`)
- **Packages**: lowercase (`com.example.orchestrator.tool.impl`)
- **Records**: PascalCase with descriptive names (`ToolResult`, `TaskPlan`)

### API Design
- RESTful endpoints under `/api/`
- JSON request/response bodies
- Standard HTTP status codes
- Validation via `@Valid` and Bean Validation
- Consistent error responses

### Testing
- Unit tests in `src/test/java/...`
- `@TempDir` for filesystem isolation
- `@ActiveProfiles("test")` for test profile
- `@Disabled` for tests requiring external services
- Mock external dependencies where possible

### Git Workflow
- Feature branches from main
- Conventional commits (feat:, fix:, docs:, refactor:, test:)
- PR required for merge
- All tests must pass

### Documentation
- Javadoc for public APIs
- .ai/ memory files for project context
- CHANGELOG.md for version history
- ARCHITECTURE.md for system design
- DECISIONS.md for architectural decisions