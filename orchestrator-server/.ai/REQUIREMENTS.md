# Requirements

## Functional Requirements

### FR-001: Project Management
- Register projects with unique ID, name, root path, build tool
- List all registered projects
- Get project details
- Isolate projects (no cross-project access)

### FR-002: Natural Language Task Submission
- Accept natural language engineering requests via REST API
- Convert to structured requirements (RequirementSpec)
- Generate ordered task plan (TaskPlan) with dependencies

### FR-003: Human Approval Gates
- Require explicit human approval before write operations
- Require explicit human approval before release/deployment
- Approval via REST API (approve/reject with comments)
- Async approval with timeout (default 30 min)

### FR-004: Controlled Code Modification
- Read files (SAFE)
- Write files with approval (CONTROLLED)
- Edit files with approval (CONTROLLED)
- Run Maven build/test (CONTROLLED)
- Run security scans (CONTROLLED)

### FR-005: Verification Pipeline
- Compile (Maven compile)
- Unit Tests (Maven test)
- Static Analysis (Semgrep)
- Dependency Vulnerability Scan (OWASP Dependency-Check)
- Container/Image Scan (Trivy)
- Secret Scanning
- Docker Build
- Docker Image Scan

### FR-006: Automated Repair Loop
- Bounded retry (max 3 attempts default)
- Max attempt timeout (10 min default)
- Total timeout (30 min default)
- Human approval required before each attempt
- Record all attempts with artifacts

### FR-007: Project Memory
- 14 standard memory files per project
- Priority loading (CURRENT_STATE, TODO, ARCHITECTURE, DECISIONS, KNOWN_ISSUES, DEVELOPMENT_LOG)
- Read/write via ProjectMemoryService

### FR-008: Context Selection
- Deterministic file selection (dir scan → keyword search → imports → memory)
- Configurable limits (max 200 files, 500KB total)
- Excludes build artifacts, .git, node_modules, etc.

### FR-009: Audit Logging
- Immutable append-only log
- Records: task creation, approvals, tool executions, verification results, repair attempts
- No secrets in logs

## Non-Functional Requirements

### NFR-001: Security
- No arbitrary shell execution
- Path containment validation
- No secrets in logs/artifacts
- Permission enforcement at tool execution
- Human-only for destructive operations

### NFR-002: Reliability
- Bounded repair attempts (max 3 default)
- Timeouts on all external calls
- Graceful degradation when external tools unavailable
- Atomic artifact writes

### NFR-003: Observability
- Structured audit logging (JSONL)
- Structured verification results
- Health endpoint (/actuator/health)
- Metrics via Micrometer

### NFR-004: Performance
- Context selection < 500ms
- Tool execution timeouts (60-300s)
- Max context size 500KB
- Max file read 100KB

### NFR-005: Maintainability
- Modular monolith architecture
- Clear module boundaries
- Comprehensive test coverage (32 tests)
- Structured logging

## Security Requirements
- No arbitrary shell execution
- Path containment validation
- Secret masking in logs/output
- No hardcoded secrets
- Human approval for destructive operations
- No git push, deploy, or destructive actions by AI