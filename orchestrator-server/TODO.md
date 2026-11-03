# Orchestrator Project - 100% Completion TODO List

**Current State**: ~85% complete  
**Target**: Production-ready with all P0/P1/P2 items and testing complete

---

## P0 - Production Blockers (Must complete before production deployment)

### 1. Persist CostTracker to SQLite Database
**Description**: CostTracker currently uses in-memory `ConcurrentHashMap` and `AtomicLong` - all cost data lost on restart. Need persistent storage with budget enforcement surviving restarts.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/model/router/CostTracker.java` - Refactor to use repository
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/` - Add new entity classes
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/` - Add new repository interfaces
- `orchestrator/src/main/resources/db/migration/V2__cost_tracking.sql` - New migration file
- `orchestrator/src/main/resources/application.yml` - Add SQLite config for production

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/TaskCostEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/DailyCostEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/CostAggregateEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/TaskCostRepository.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/DailyCostRepository.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/CostAggregateRepository.java`

**Database Schema Changes** (V2__cost_tracking.sql):
```sql
CREATE TABLE task_costs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    provider VARCHAR(64),
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,  -- Store in micro-dollars (1/1,000,000)
    start_time TIMESTAMP NOT NULL,
    last_update TIMESTAMP NOT NULL
);
CREATE INDEX idx_task_cost_task ON task_costs(task_id);
CREATE INDEX idx_task_cost_provider ON task_costs(provider);
CREATE INDEX idx_task_cost_last_update ON task_costs(last_update);

CREATE TABLE daily_costs (
    date DATE NOT NULL PRIMARY KEY,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE cost_aggregates (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    total_prompt_tokens BIGINT DEFAULT 0,
    total_completion_tokens BIGINT DEFAULT 0,
    total_cost_usd_micro BIGINT DEFAULT 0,
    daily_cost_usd_micro BIGINT DEFAULT 0,
    last_reset TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

**Configuration Changes**:
- Add `spring.datasource.url=jdbc:sqlite:orchestrator.db` for production profile
- Ensure Flyway runs migrations on startup

**Estimated Effort**: 8-12 hours  
**Dependencies**: None  
**Risk**: Medium - migration must handle existing in-memory data gracefully

---

### 2. Persist ApprovalService to SQLite + Audit Trail
**Description**: ApprovalService uses in-memory `ConcurrentHashMap` - all pending approvals and decisions lost on restart. Need persistent storage with full audit trail.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/approval/ApprovalService.java` - Refactor to use repository
- `orchestrator/src/main/java/com/example/orchestrator/approval/ApprovalController.java` - Update to use persisted data
- `orchestrator/src/main/java/com/example/orchestrator/api/TaskController.java` - Update approval endpoints
- `orchestrator/src/main/resources/db/migration/V3__approval_tracking.sql` - New migration file

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/ApprovalEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/ApprovalAuditEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/ApprovalRepository.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/ApprovalAuditRepository.java`

**Database Schema Changes** (V3__approval_tracking.sql):
```sql
CREATE TABLE approvals (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,  -- PENDING, APPROVED, REJECTED, TIMEOUT
    approver VARCHAR(128),
    comment VARCHAR(4000),
    requested_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_approval_task ON approvals(task_id);
CREATE INDEX idx_approval_status ON approvals(status);
CREATE INDEX idx_approval_requested ON approvals(requested_at);

CREATE TABLE approval_audit (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,  -- REQUESTED, APPROVED, REJECTED, TIMEOUT, CANCELLED
    actor VARCHAR(128) NOT NULL,  -- user, system, api
    comment VARCHAR(4000),
    timestamp TIMESTAMP NOT NULL
);
CREATE INDEX idx_approval_audit_approval ON approval_audit(approval_id);
CREATE INDEX idx_approval_audit_timestamp ON approval_audit(timestamp);
```

**Configuration Changes**: None beyond standard DB config

**Estimated Effort**: 6-8 hours  
**Dependencies**: P0.1 (shared DB infrastructure)  
**Risk**: Medium - must handle concurrent approval requests correctly

---

### 3. Add JWT/OAuth Authentication to REST Endpoints
**Description**: Current API uses simple API key (`ApiKeyAuthService`). Need JWT/OAuth for production with role-based access, token refresh, and secure defaults.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/security/ApiKeyAuthService.java` - Replace/extend with JWT service
- `orchestrator/src/main/java/com/example/orchestrator/security/SecurityPolicy.java` - Add JWT validation
- `orchestrator/src/main/java/com/example/orchestrator/api/TaskController.java` - Add `@PreAuthorize` annotations
- `orchestrator/src/main/java/com/example/orchestrator/api/CostTrackingController.java` - Add security
- `orchestrator/src/main/java/com/example/orchestrator/approval/ApprovalController.java` - Add security
- `orchestrator/src/main/java/com/example/orchestrator/cli/OrchestratorCli.java` - Add JWT token acquisition
- `orchestrator/src/main/resources/application.yml` - Add JWT config
- `orchestrator/.env.example` - Add JWT secret/env vars

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/security/JwtAuthService.java`
- `orchestrator/src/main/java/com/example/orchestrator/security/JwtTokenFilter.java`
- `orchestrator/src/main/java/com/example/orchestrator/security/SecurityConfig.java` (Spring Security config)
- `orchestrator/src/main/java/com/example/orchestrator/security/JwtProperties.java`
- `orchestrator/src/main/java/com/example/orchestrator/api/AuthController.java` (login/refresh endpoints)

**Dependencies to Add** (pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

**Configuration Changes** (application.yml):
```yaml
orchestrator:
  security:
    jwt:
      secret: ${JWT_SECRET:}  # Required in production
      expiration-minutes: 60
      refresh-expiration-hours: 168  # 7 days
      issuer: orchestrator
    oauth2:
      enabled: false
      client-id: ${OAUTH2_CLIENT_ID:}
      client-secret: ${OAUTH2_CLIENT_SECRET:}
      authorization-uri: ${OAUTH2_AUTH_URI:}
      token-uri: ${OAUTH2_TOKEN_URI:}
      jwk-set-uri: ${OAUTH2_JWK_URI:}
```

**Estimated Effort**: 12-16 hours  
**Dependencies**: P0.1, P0.2 (for user storage if OAuth)  
**Risk**: High - security-critical, requires thorough testing

---

### 4. Keys Already Rotated (.env.example created)
**Status**: ✅ **COMPLETE** - `.env.example` exists with placeholders

**Verification Needed**:
- Confirm no real keys in `.env` or git history
- Verify `.gitignore` excludes `.env`
- Ensure CI/CD uses proper secret management

**Estimated Effort**: 0.5 hours (verification only)

---

## P1 - Hardening (Production reliability & observability)

### 1. Rate Limiting on CLI and REST API
**Description**: Prevent abuse and ensure fair usage with configurable rate limits per endpoint/client.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/api/TaskController.java`
- `orchestrator/src/main/java/com/example/orchestrator/api/CostTrackingController.java`
- `orchestrator/src/main/java/com/example/orchestrator/approval/ApprovalController.java`
- `orchestrator/src/main/java/com/example/orchestrator/cli/OrchestratorCli.java`
- `orchestrator/src/main/resources/application.yml`

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/security/RateLimitFilter.java`
- `orchestrator/src/main/java/com/example/orchestrator/security/RateLimitConfig.java`
- `orchestrator/src/main/java/com/example/orchestrator/security/RateLimitProperties.java`

**Dependencies to Add** (pom.xml):
```xml
<dependency>
    <groupId>io.github.bucket4j</groupId>
    <artifactId>bucket4j-spring-boot3-starter</artifactId>
    <version>1.8.0</version>
</dependency>
```

**Configuration Changes** (application.yml):
```yaml
orchestrator:
  rate-limit:
    enabled: true
    default:
      requests-per-minute: 60
      requests-per-hour: 1000
    endpoints:
      "/api/tasks/**":
        requests-per-minute: 30
      "/api/costs/**":
        requests-per-minute: 60
      "/tasks/**":
        requests-per-minute: 30
    cli:
      requests-per-minute: 20
```

**Estimated Effort**: 4-6 hours  
**Dependencies**: P0.3 (JWT for client identification)  
**Risk**: Low

---

### 2. Structured Audit Logging for All Tool Executions
**Description**: Current `AuditService` logs to file as JSON lines. Need structured logging with correlation IDs, searchable fields, and integration with observability stack.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/audit/AuditService.java` - Enhance with structured fields
- `orchestrator/src/main/java/com/example/orchestrator/tool/ToolRegistry.java` - Add correlation ID propagation
- `orchestrator/src/main/java/com/example/orchestrator/execution/ExecutionEngine.java` - Pass correlation context
- `orchestrator/src/main/resources/application.yml` - Add structured logging config

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/audit/AuditEvent.java` (record with all fields)
- `orchestrator/src/main/java/com/example/orchestrator/audit/StructuredAuditService.java`
- `orchestrator/src/main/java/com/example/orchestrator/audit/AuditCorrelationFilter.java` (Servlet filter for correlation IDs)

**Database Schema Changes** (optional - for queryable audit log):
```sql
CREATE TABLE audit_events (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    correlation_id VARCHAR(64),
    event_type VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    project_id VARCHAR(128),
    user_id VARCHAR(128),
    actor_type VARCHAR(32),  -- human, ai, system
    payload_json TEXT,
    severity VARCHAR(16),  -- INFO, WARN, ERROR, CRITICAL
    timestamp TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_correlation ON audit_events(correlation_id);
CREATE INDEX idx_audit_task ON audit_events(task_id);
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp);
CREATE INDEX idx_audit_type ON audit_events(event_type);
```

**Estimated Effort**: 6-8 hours  
**Dependencies**: P0.1, P0.2 (shared DB)  
**Risk**: Low

---

### 3. Fix NvidiaProvider Cost Tracking NPE
**Description**: NvidiaProvider may return null usage fields causing NPE in cost tracking. Need null-safe handling.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/model/router/provider/NvidiaProvider.java` - Ensure non-null usage in response
- `orchestrator/src/main/java/com/example/orchestrator/model/router/ModelRouter.java` - Lines 156-184 already have null checks but verify completeness

**Root Cause Analysis** (from ModelRouter.java:156-184):
```java
// Current code has null checks but usage() itself could be null
if (response.usage() != null) {
    long promptTokens = response.usage().promptTokens() != null ? response.usage().promptTokens() : 0L;
    long completionTokens = response.usage().completionTokens() != null ? response.usage().completionTokens() : 0L;
    // ...
}
```

**Fix**: Ensure `NvidiaUsage` record handles null fields, and `toChatResponse` provides defaults.

**Estimated Effort**: 1-2 hours  
**Dependencies**: None  
**Risk**: Low

---

### 4. Health Check Endpoint for ModelRouter Providers
**Description**: Need `/actuator/health` style endpoint showing provider availability, circuit breaker state, and latency.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/model/router/ModelRouter.java` - Add health check method
- `orchestrator/src/main/resources/application.yml` - Enable actuator endpoints

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/model/router/ProviderHealthIndicator.java` (implements `HealthIndicator`)
- `orchestrator/src/main/java/com/example/orchestrator/model/router/dto/ProviderHealth.java`

**Dependencies to Add** (pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Configuration Changes** (application.yml):
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
```

**Estimated Effort**: 3-4 hours  
**Dependencies**: None  
**Risk**: Low

---

## P2 - Quality of Life (Developer experience & features)

### 1. ContextManager Caching (Avoid Full Project Scan Per Request)
**Description**: `ContextManager.selectContext()` scans entire project on every request. Add caching with invalidation on file changes.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/context/ContextManager.java` - Add caching layer
- `orchestrator/src/main/resources/application.yml` - Cache config

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/context/ContextCache.java`
- `orchestrator/src/main/java/com/example/orchestrator/context/FileChangeDetector.java` (uses WatchService)

**Dependencies to Add** (pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**Configuration Changes** (application.yml):
```yaml
orchestrator:
  context:
    cache:
      enabled: true
      max-size: 100
      ttl-minutes: 30
      scan-interval-seconds: 60
```

**Estimated Effort**: 6-8 hours  
**Dependencies**: None  
**Risk**: Medium - cache invalidation complexity

---

### 2. RepairLoopService - Automated Fix/Verify Cycles
**Description**: `RepairLoopService` exists but needs enhancement for production use - better error handling, progress reporting, and integration with verification.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/repair/RepairLoopService.java` - Enhance reliability
- `orchestrator/src/main/java/com/example/orchestrator/repair/RepairLoopConfig.java` - Add more config options
- `orchestrator/src/main/java/com/example/orchestrator/verification/VerificationController.java` - Ensure compatibility

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/repair/RepairProgressListener.java` (interface for progress callbacks)
- `orchestrator/src/main/java/com/example/orchestrator/repair/RepairStrategy.java` (pluggable repair strategies)

**Enhancements Needed**:
- Exponential backoff between attempts
- Partial success handling (some verification steps pass)
- Repair history analysis for learning
- Integration with CostTracker for budget-aware repairs

**Estimated Effort**: 10-14 hours  
**Dependencies**: P0.1 (cost tracking persistence)  
**Risk**: Medium

---

### 3. Web UI / Dashboard (React/Vue + REST API)
**Description**: Build a web dashboard for monitoring tasks, costs, approvals, and system health.

**Files to Modify**:
- `orchestrator/src/main/resources/application.yml` - CORS config for UI
- `orchestrator/pom.xml` - Add frontend build plugin (optional)

**New Files/Directory Needed**:
```
orchestrator/
├── ui/                    # New directory for frontend
│   ├── package.json
│   ├── src/
│   │   ├── components/
│   │   │   ├── TaskList.jsx
│   │   │   ├── TaskDetail.jsx
│   │   │   ├── CostDashboard.jsx
│   │   │   ├── ApprovalQueue.jsx
│   │   │   ├── ProviderHealth.jsx
│   │   │   └── ProjectList.jsx
│   │   ├── services/
│   │   │   └── api.js
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── index.html
│   └── vite.config.js
```

**API Endpoints Needed** (add to existing controllers):
- `GET /api/dashboard/summary` - Aggregate stats
- `GET /api/dashboard/tasks` - Paginated task list with filters
- `GET /api/dashboard/costs/trends` - Cost over time
- `GET /api/dashboard/approvals/pending` - Pending approvals
- `GET /api/dashboard/providers/health` - Provider health status

**Estimated Effort**: 20-30 hours  
**Dependencies**: P0.1, P0.2, P0.3 (auth), P1.4 (health checks)  
**Risk**: Medium-High (new tech stack)

---

### 4. Multi-Project Workspace Support
**Description**: Support multiple projects in a single orchestrator instance with isolation.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/project/Project.java` - Add workspaceId
- `orchestrator/src/main/java/com/example/orchestrator/project/ProjectManager.java` - Workspace-aware methods
- `orchestrator/src/main/java/com/example/orchestrator/api/TaskController.java` - Workspace context
- `orchestrator/src/main/java/com/example/orchestrator/context/ContextManager.java` - Workspace-scoped scanning
- `orchestrator/src/main/resources/db/migration/V4__multi_workspace.sql` - Schema changes

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/project/Workspace.java`
- `orchestrator/src/main/java/com/example/orchestrator/project/WorkspaceManager.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/entity/WorkspaceEntity.java`
- `orchestrator/src/main/java/com/example/orchestrator/persistence/repo/WorkspaceRepository.java`

**Database Schema Changes** (V4__multi_workspace.sql):
```sql
CREATE TABLE workspaces (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(4000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE projects ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
CREATE INDEX idx_project_workspace ON projects(workspace_id);

ALTER TABLE execution_states ADD COLUMN workspace_id VARCHAR(128);
CREATE INDEX idx_execution_workspace ON execution_states(workspace_id);

ALTER TABLE project_memory ADD COLUMN workspace_id VARCHAR(128);
CREATE INDEX idx_memory_workspace ON project_memory(workspace_id);

ALTER TABLE repair_sessions ADD COLUMN workspace_id VARCHAR(128);
CREATE INDEX idx_repair_workspace ON repair_sessions(workspace_id);
```

**Estimated Effort**: 12-16 hours  
**Dependencies**: P0.1, P0.2 (DB persistence)  
**Risk**: Medium - schema migration complexity

---

### 5. Streaming Response Support in CLI
**Description**: CLI currently blocks on full response. Add streaming output for long-running tasks.

**Files to Modify**:
- `orchestrator/src/main/java/com/example/orchestrator/cli/GenerateCommand.java`
- `orchestrator/src/main/java/com/example/orchestrator/cli/TaskCommand.java`
- `orchestrator/src/main/java/com/example/orchestrator/model/router/ModelRouter.java` - Already supports streaming
- `orchestrator/src/main/java/com/example/orchestrator/execution/ExecutionEngine.java` - Stream progress

**New Files Needed**:
- `orchestrator/src/main/java/com/example/orchestrator/cli/StreamingOutputHandler.java`
- `orchestrator/src/main/java/com/example/orchestrator/execution/StreamingExecutionCallback.java`

**Estimated Effort**: 4-6 hours  
**Dependencies**: None  
**Risk**: Low

---

## Testing (Critical for production confidence)

### E2E Tests with Real NVIDIA API
**Description**: End-to-end tests using actual NVIDIA API to verify full pipeline.

**Files to Create**:
- `orchestrator/src/test/java/com/example/orchestrator/integration/NvidiaE2ETest.java`
- `orchestrator/src/test/resources/application-e2e.yml`

**Test Scenarios**:
- Full task execution: submit → plan → execute → verify
- Cost tracking accuracy with real API calls
- Provider failover (NVIDIA → OpenRouter fallback)
- Approval workflow with real delays
- Repair loop with real verification failures

**Configuration**: Use test NVIDIA API key, separate test database

**Estimated Effort**: 12-16 hours  
**Dependencies**: P0.1, P0.2, P0.3, P1.3, P1.4  
**Risk**: High - external dependency, flaky tests possible

---

### Integration Tests with External Tools (Docker, Semgrep, Trivy, DependencyCheck)
**Description**: Test actual tool execution in containers/VMs.

**Files to Create**:
- `orchestrator/src/test/java/com/example/orchestrator/integration/DockerIntegrationTest.java`
- `orchestrator/src/test/java/com/example/orchestrator/integration/SemgrepIntegrationTest.java`
- `orchestrator/src/test/java/com/example/orchestrator/integration/TrivyIntegrationTest.java`
- `orchestrator/src/test/java/com/example/orchestrator/integration/DependencyCheckIntegrationTest.java`
- `orchestrator/src/test/resources/test-projects/` - Sample projects for testing

**Test Scenarios**:
- Docker build/push/scan with real Docker daemon
- Semgrep rule execution with known vulnerable code
- Trivy vulnerability scanning on test images
- DependencyCheck on projects with known CVEs

**CI/CD Requirements**: GitHub Actions with Docker, self-hosted runners for security tools

**Estimated Effort**: 16-20 hours  
**Dependencies**: Tool availability in CI environment  
**Risk**: High - environment dependencies, slow execution

---

### Load Testing
**Description**: Verify system handles concurrent tasks, API requests, and cost tracking under load.

**Files to Create**:
- `orchestrator/load-test/` - k6 or JMeter test scripts
- `orchestrator/src/test/java/com/example/orchestrator/load/LoadTestConfiguration.java`

**Scenarios**:
- 50 concurrent task submissions
- 100 concurrent API requests to cost/approval endpoints
- Sustained load for 30 minutes
- Cost tracking accuracy under load
- Circuit breaker behavior under provider failures

**Tools**: k6 (recommended) or JMeter

**Estimated Effort**: 8-12 hours  
**Dependencies**: All P0/P1 complete  
**Risk**: Medium - requires staging environment

---

## Summary

| Priority | Item | Effort (hours) | Dependencies |
|----------|------|----------------|--------------|
| P0 | CostTracker SQLite Persistence | 8-12 | None |
| P0 | ApprovalService SQLite + Audit | 6-8 | P0.1 |
| P0 | JWT/OAuth Authentication | 12-16 | P0.1, P0.2 |
| P0 | Keys Rotated (verify) | 0.5 | None |
| P1 | Rate Limiting | 4-6 | P0.3 |
| P1 | Structured Audit Logging | 6-8 | P0.1, P0.2 |
| P1 | Fix NvidiaProvider NPE | 1-2 | None |
| P1 | Health Check Endpoints | 3-4 | None |
| P2 | ContextManager Caching | 6-8 | None |
| P2 | RepairLoopService Enhancement | 10-14 | P0.1 |
| P2 | Web UI Dashboard | 20-30 | P0.1, P0.2, P0.3, P1.4 |
| P2 | Multi-Project Workspace | 12-16 | P0.1, P0.2 |
| P2 | CLI Streaming | 4-6 | None |
| Test | E2E with Real NVIDIA API | 12-16 | All P0/P1 |
| Test | External Tool Integration | 16-20 | CI environment |
| Test | Load Testing | 8-12 | All P0/P1 |

**Total Estimated Effort**: ~150-200 hours

**Recommended Order**:
1. P0.1 → P0.2 → P0.3 (core persistence & auth)
2. P1.3 → P1.4 (quick wins)
3. P1.1 → P1.2 (hardening)
4. P2.1 → P2.5 (quality of life, parallelize where possible)
5. All Testing (after P0/P1 complete)

---

## Quick Wins (Can start immediately)
- [ ] P0.4: Verify key rotation (30 min)
- [ ] P1.3: Fix NvidiaProvider NPE (1-2 hrs)
- [ ] P1.4: Health check endpoints (3-4 hrs)

## Blockers for Other Work
- P0.1 blocks: P0.2, P2.2, P2.4
- P0.3 blocks: P1.1, P2.3
- P0.1 + P0.2 block: All Testing