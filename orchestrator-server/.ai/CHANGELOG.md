# Changelog

## [0.3.0] - 2026-09-22 - Phase 3 Complete

### Added
- **Security Scanning Tools (4)**:
  - `SemgrepScanTool` - Static analysis with configurable rulesets
  - `DependencyCheckTool` - OWASP Dependency-Check for vulnerable dependencies
  - `TrivyScanTool` - Filesystem and container image vulnerability scanning
  - `SecretScanTool` - Secret/credential detection with 11 built-in patterns

- **Docker Verification (2 tools)**:
  - `DockerBuildTool` - Build Docker images with configurable tags, build args
  - `DockerImageScanTool` - Scan Docker images for vulnerabilities using Trivy

- **Automated Repair Loop**:
  - `RepairLoopService` - Orchestrates bounded repair attempts
  - `RepairLoopConfig` - Configurable max attempts (3), attempt timeout (10min), total timeout (30min)
  - `RepairSession` - State machine: PENDING → ATTEMPT_IN_PROGRESS → ATTEMPT_FAILED/COMPLETED_SUCCESS/COMPLETED_FAILED/TIMEOUT/CANCELLED
  - `RepairAttemptRecord` - Captures attempt metadata (status, failure reason, steps, duration)
  - `RepairAttemptRepository` - In-memory storage with query by repairId/taskId

- **Enhanced Tracking**:
  - `ArtifactStore` - Persists artifacts to filesystem with metadata
  - `RepairAttemptRepository` - In-memory storage with query by repairId/taskId
  - `VerificationResult` - Enhanced with repairId, failureSummary

- **Permission Enforcement**:
  - `PermissionManager` with 4 risk levels (SAFE, CONTROLLED, APPROVAL_REQUIRED, HUMAN_ONLY)
  - Enforced in `ToolRegistry.execute()` for all tool calls
  - `PermissionContext` tracks userId, taskId, hasApproval, actorType

- **Verification Pipeline Extended**:
  - Steps: compile → unit-test → semgrep → dependency-check → trivy → secret-scan → docker-build → docker-image-scan
  - Security scans advisory (don't fail fast)
  - Docker build required to pass

### Changed
- `ToolRegistry` - Auto-registration of all 13 tools, permission enforcement in execute()
- `ExecutionEngine` - Added PermissionManager, updated executePlan for all tools
- `VerificationController` - Added Docker steps, updated config
- `VerificationConfig` - Added dockerBuildEnabled, dockerImageScanEnabled
- `VerificationResult` - Added getFailureSummary(), repairId
- `RepairSession` - Added project field
- `RepairLoopService` - Fixed session updates, persistAttempt signature

### Fixed
- `RepairLoopService` - Session variable redefinition fixed
- `persistAttempt` signature fixed to accept VerificationResult
- `RepairSession` - Added project field
- `PermissionManagerTest` - Added @ActiveProfiles("test")
- `RepairLoopServiceTest` - Added ProjectManager import
- `DockerBuildToolTest` - Added ToolSpec import
- `ExecutionIntegrationTest` - @Disabled (requires API key)
- `mvn verify` - BUILD SUCCESS

### Added
- `jackson-datatype-jsr310` dependency for Instant serialization

### Tests
- 32 tests total (31 pass, 1 skipped - requires API key)
- New tests: PermissionManager (4), RepairLoopService (4), all 6 new tools, VerificationController (3)

## [0.2.0] - 2026-09-22 - Phase 2 Complete

### Added
- **CONTROLLED Tools**: writeFile, editFile, runMavenBuild
- **PermissionManager** - Risk level enforcement (SAFE, CONTROLLED, APPROVAL_REQUIRED, HUMAN_ONLY)
- **VerificationController** - Pipeline: compile → unit test → Semgrep
- **Approval Service** - Extended to write operations
- **ArtifactStore** - Persist attempts/artifacts
- **Integration Test** - Temporary Git repo

### Changed
- ToolRegistry - Auto-registration, permission enforcement
- ExecutionEngine - PermissionContext per tool call
- ToolRegistry - Auto-registration of all tools

### Tests
- 15 tests (14 pass, 1 skipped)

## [0.1.0] - 2026-09-22 - Phase 1 Complete

### Added
- Project domain model and ProjectManager
- ProjectMemoryService for .ai/ memory files
- ContextManager with deterministic relevance selection
- RequirementCompiler with structured JSON output
- TaskPlanner with TaskPlan domain model
- ToolRegistry and SAFE tool implementations
- ApprovalService and REST endpoints
- ExecutionEngine and AuditService
- TaskController REST API
- Disable CommandLineRunner in test profile

### Changed
- Added Spring Web, Validation, Actuator dependencies
- Disabled CommandLineRunner in test profile

### Tests
- 14 tests pass (1 skipped - requires API key)
- mvn verify: BUILD SUCCESS