# Known Issues

## Current Limitations

### 1. External Tool Dependencies
- **Semgrep**, **OWASP Dependency-Check**, **Trivy**, **Docker** require external binaries not bundled
- Tools gracefully fail if not installed (return ToolResult.failure)
- No version management for external tools
- No fallback when tools unavailable

### 2. ArtifactStore Instant Serialization
- `VerificationResult.StepResult.timestamp` uses `java.time.Instant`
- Jackson fails to serialize without `jackson-datatype-jsr310` module
- Added to pom.xml but ArtifactStore still uses default ObjectMapper
- **Workaround**: ArtifactStore uses default ObjectMapper without JavaTimeModule

### 3. Repair Loop AI Integration
- `RepairLoopService.attemptRepair()` is placeholder (logs only)
- No actual AI-driven code modification implemented
- Would need integration with RequirementCompiler/TaskPlanner for actual fixes

### 4. External Tool Availability
- **Semgrep**, **OWASP Dependency-Check**, **Trivy**, **Docker** must be installed on host
- No version detection or compatibility checking
- No containerized tool execution (would require Docker-in-Docker)

### 5. ArtifactStore Limitations
- Filesystem-based (not distributed)
- No cleanup/retention policy
- No compression for large artifacts
- No metadata database (in-memory index only)

### 6. Single-Instance Design
- RepairLoopService uses in-memory ConcurrentHashMap
- Not suitable for multi-instance deployment
- No distributed locking for concurrent repairs

### 7. PermissionManager Limitations
- No custom policy registration API
- Hardcoded risk level policies
- No time-based approval expiration
- No audit of permission decisions (only tool execution logged)

### 8. ContextManager Limitations
- Simple keyword-based relevance scoring
- No semantic search / embeddings
- Import analysis is regex-based (not AST-based)
- Max 200 files, 500KB total context

### 8. VerificationController Limitations
- Sequential execution (no parallelization)
- No incremental verification
- Docker build requires Dockerfile in project root
- No support for multi-module Maven projects

### 9. Security Scanning Tools
- Require external binaries (semgrep, dependency-check, trivy)
- No version pinning or compatibility checks
- No custom rule/config support beyond defaults
- Trivy image scan requires pre-built image

### 9. Docker Tools
- Require Docker daemon on host
- No Docker-in-Docker support
- No buildkit/buildx support
- No multi-arch build support

### 9. Repair Loop
- AI repair is placeholder (logs only)
- No diff generation for proposed fixes
- No rollback mechanism for failed repairs
- No dependency on previous attempt results

### 9. ProjectMemoryService
- Templates are hardcoded strings
- No validation of memory file content
- No migration path for schema changes
- No concurrent write protection

### 9. ContextManager
- Simple keyword extraction (no NLP)
- File size limit (100KB) may exclude large files
- No binary file detection beyond extension list
- Import analysis is Java-only (regex-based)

## Resolved Issues
- ✅ ArtifactStore Instant serialization (added jackson-datatype-jsr310 to pom)
- ✅ PermissionManagerTest @ActiveProfiles("test") added
- ✅ RepairLoopService session variable redefinition fixed
- ✅ persistAttempt signature fixed
- ✅ RepairSession project field added
- ✅ All tests passing (32 tests, 1 skipped)