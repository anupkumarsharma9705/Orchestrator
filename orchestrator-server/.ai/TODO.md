# TODO

## Completed (Phase 1-3)

- [x] Project domain model and ProjectManager
- [x] ProjectMemoryService for .ai/ memory files
- [x] ContextManager with deterministic relevance selection
- [x] RequirementCompiler with structured JSON output
- [x] TaskPlanner with TaskPlan domain model
- [x] ToolRegistry and SAFE tool implementations (listDirectory, readFile, searchFiles, gitStatus, gitDiff, runMavenTest)
- [x] ApprovalService and REST endpoints
- [x] ExecutionEngine and AuditService
- [x] TaskController REST API
- [x] Disable CommandLineRunner in test profile
- [x] Unit and integration tests for Phase 1
- [x] mvn verify validation for Phase 1

- [x] CONTROLLED tools (writeFile, editFile, runMavenBuild)
- [x] PermissionManager with risk level enforcement
- [x] VerificationController (compile -> unit -> Semgrep)
- [x] Extended approval to write operations
- [x] ArtifactStore for attempt/artifact persistence
- [x] Integration test with temporary Git repo
- [x] mvn verify validation for Phase 2

- [x] Security Scanning tools (Semgrep, OWASP Dependency-Check, Trivy, Secret scanning)
- [x] Docker verification capabilities with PermissionManager enforcement
- [x] Bounded automated repair loop with max attempts and timeout
- [x] Enhanced attempt/artifact tracking with detailed persistence
- [x] PermissionManager enforcement for all new operations
- [x] Comprehensive tests for Phase 3 features
- [x] mvn verify validation for Phase 3

## Remaining (Phase 4+)

### Phase 4 - Model Router
- [ ] ModelRouter with provider abstraction (OpenRouter, Ollama, Kaggle)
- [ ] Role-based model profiles (PLANNER, CODER, REVIEWER, SECURITY_REASONING, DOCUMENTATION)
- [ ] Provider failover and circuit breaker
- [ ] Cost/token tracking per model
- [ ] Configuration-driven model selection

### Phase 5 - External Adapters
- [ ] OpenCode adapter
- [ ] Aider adapter
- [ ] CLI adapter
- [ ] MCP integration

### Phase 6 - Release Management
- [ ] ReleaseManager with state machine
- [ ] Human approval gates for release
- [ ] Git integration (commit, tag, push - HUMAN_ONLY)
- [ ] Deployment integration (HUMAN_ONLY)
- [ ] Audit log completeness verification

### Phase 7 - Advanced Intelligence (Future)
- [ ] RAG/vector search for repository indexing
- [ ] Advanced context selection with embeddings
- [ ] Multi-project optimization
- [ ] Observability dashboard (metrics, tracing)
- [ ] React UI

### Technical Debt / Improvements
- [ ] Fix ArtifactStore Instant serialization (add jackson-datatype-jsr310 for VerificationResult StepResult timestamps)
- [ ] Replace RepairLoopService placeholder with real AI integration
- [ ] Add integration tests with real Semgrep/DependencyCheck/Trivy/Docker
- [ ] Add Docker daemon detection and graceful degradation
- [ ] Implement ArtifactStore with database backend option
- [ ] Add structured logging (JSON) and metrics export
- [ ] Implement custom policy registration in PermissionManager
- [ ] Add distributed locking for RepairLoopService (multi-instance)
- [ ] Implement ArtifactStore with S3/MinIO backend option
- [ ] Add OpenTelemetry tracing
- [ ] Implement circuit breaker for external tool calls