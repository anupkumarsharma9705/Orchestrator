# Database Design
## Java Companion Orchestrator — v1 CLI MVP

---

## 1. Overview

### 1.1 Database Choice
| Environment | Database | Rationale |
|-------------|----------|-----------|
| **Production** | SQLite (file-based) | Zero-config, portable, single-file, Flyway-compatible |
| **Test** | H2 (in-memory) | Fast, isolated, CI-friendly |

### 1.2 ORM & Migrations
- **ORM**: Spring Data JPA (Hibernate)
- **Migrations**: Flyway (versioned, never edit applied migrations)
- **Dialect**: `SQLiteDialect` (prod) / `H2Dialect` (test)
- **DDL Auto**: `validate` (prod) / `create-drop` (test)

---

## 2. Entity Relationship Diagram

```text
┌─────────────┐       ┌─────────────┐       ┌─────────────┐
│   Project   │       │     Job      │       │   Round     │
├─────────────┤       ├─────────────┤       ├─────────────┤
│ id (PK)     │1   N  │ id (PK)     │1   N  │ id (PK)     │
│ name        │◄──────│ project_id  │◄──────│ job_id (FK) │
│ root_path   │       │ state       │       │ round_num   │
│ build_tool  │       │ round       │       │ build_ok    │
│ test_cmd    │       │ budget_usd  │       │ tests_passed│
│ environment │       │ cost_usd    │       │ tests_failed│
└─────────────┘       │ created_at  │       │ coverage    │
                      │ finished_at │       │ cost_usd    │
                      └──────┬──────┘       │ duration_sec│
                             │              └──────┬──────┘
                    ┌───────┴───────┐             │
                    ▼               ▼             ▼
             ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
             │  Finding    │ │     Fix     │ │  Approval   │
             ├─────────────┤ ├─────────────┤ ├─────────────┤
             │ id (PK)     │ │ id (PK)     │ │ id (PK)     │
             │ job_id (FK) │ │ job_id (FK) │ │ job_id (FK) │
             │ fingerprint │ │ finding_fp  │ │ status      │
             │ tool        │ │ round       │ │ approver    │
             │ rule_id     │ │ summary     │ │ comment     │
             │ severity    │ │ files_chg   │ │ requested_at│
             │ file/line   │ │ result      │ │ decided_at  │
             └─────────────┘ └─────────────┘ └─────────────┘
```

---

## 2. Flyway Migration Scripts

### V1__initial_schema.sql
```sql
-- Jobs table
CREATE TABLE jobs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL,  -- CREATED, CLARIFYING, REQUIREMENTS_READY, PROMPT_REFINED, PLANNED, AWAITING_APPROVAL, BUILDING, VERIFYING, FIXING, COMPLETED, FAILED, CANCELLED
    round INT DEFAULT 1,
    budget_usd DECIMAL(10,6),
    cost_usd DECIMAL(10,6) DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    error TEXT,
    CONSTRAINT chk_job_state CHECK (state IN ('CREATED','CLARIFYING','REQUIREMENTS_READY','PROMPT_REFINED','PLANNED','AWAITING_APPROVAL','BUILDING','VERIFYING','FIXING','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_jobs_project ON jobs(project_id);
CREATE INDEX idx_jobs_state ON jobs(state);
CREATE INDEX idx_jobs_created ON jobs(created_at);

-- Rounds
CREATE TABLE rounds (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    round_num INT NOT NULL,
    build_ok BOOLEAN,
    tests_passed INT,
    tests_failed INT,
    coverage DECIMAL(5,2),
    cost_usd DECIMAL(10,6),
    duration_sec INT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
CREATE INDEX idx_rounds_job ON rounds(job_id);
CREATE INDEX idx_rounds_num ON rounds(job_id, round_num);

-- Findings
CREATE TABLE findings (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    tool VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128),
    severity VARCHAR(16) NOT NULL,  -- INFO, LOW, MEDIUM, HIGH, CRITICAL
    file VARCHAR(512),
    line INT,
    message TEXT,
    first_seen_round INT NOT NULL,
    fixed_in_round INT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN, FIXED, REGRESSED, ACCEPTED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
CREATE INDEX idx_findings_job ON findings(job_id);
CREATE INDEX idx_findings_fingerprint ON findings(fingerprint);
CREATE INDEX idx_findings_severity ON findings(severity);
CREATE INDEX idx_findings_status ON findings(status);
CREATE INDEX idx_findings_round ON findings(first_seen_round);

-- Fixes
CREATE TABLE fixes (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    finding_fingerprint VARCHAR(64) NOT NULL,
    round INT NOT NULL,
    summary TEXT,
    files_changed TEXT,  -- JSON array of file paths
    result VARCHAR(16) NOT NULL,  -- VERIFIED_FIXED, REVERTED, ACCEPTED, FAILED
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
CREATE INDEX idx_fixes_job ON fixes(job_id);
CREATE INDEX idx_fixes_fingerprint ON fixes(finding_fingerprint);
CREATE INDEX idx_fixes_round ON fixes(job_id, round);

-- Approvals
CREATE TABLE approvals (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,  -- PENDING, APPROVED, REJECTED, TIMEOUT
    approver VARCHAR(128),
    comment VARCHAR(4000),
    requested_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
CREATE INDEX idx_approvals_job ON approvals(job_id);
CREATE INDEX idx_approvals_status ON approvals(status);

-- Approval Audit Trail
CREATE TABLE approval_audit (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,  -- REQUESTED, APPROVED, REJECTED, TIMEOUT, CANCELLED
    actor VARCHAR(128) NOT NULL,  -- user, system, api
    comment VARCHAR(4000),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (approval_id) REFERENCES approvals(id) ON DELETE CASCADE
);
CREATE INDEX idx_audit_approval ON approval_audit(approval_id);
CREATE INDEX idx_audit_timestamp ON approval_audit(timestamp);

-- Cost Tracking (Aggregates)
CREATE TABLE cost_aggregates (
    id VARCHAR(64) NOT NULL PRIMARY KEY DEFAULT 'singleton',
    total_prompt_tokens BIGINT DEFAULT 0,
    total_completion_tokens BIGINT DEFAULT 0,
    total_cost_usd_micro BIGINT DEFAULT 0,  -- micro-dollars (1/1,000,000)
    daily_cost_usd_micro BIGINT DEFAULT 0,
    last_reset TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Task Costs (Per-Task Breakdown)
CREATE TABLE task_costs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    provider VARCHAR(64),
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    last_update TIMESTAMP NOT NULL
);
CREATE INDEX idx_task_cost_task ON task_costs(task_id);
CREATE INDEX idx_task_cost_provider ON task_costs(provider);

-- Daily Costs
CREATE TABLE daily_costs (
    date DATE NOT NULL PRIMARY KEY,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Projects
CREATE TABLE projects (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    root_path VARCHAR(1024) NOT NULL,
    memory_path VARCHAR(1024) NOT NULL,
    build_tool VARCHAR(32) NOT NULL,
    test_command VARCHAR(256),
    environment_json TEXT,  -- JSON map of env vars
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_projects_name ON projects(name);

-- Project Memory (Learnings/Conventions)
CREATE TABLE project_memory (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,  -- CONVENTION, PATTERN, LEARNING, ERROR_PATTERN
    key VARCHAR(256) NOT NULL,
    value TEXT NOT NULL,
    priority INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
CREATE INDEX idx_memory_project ON project_memory(project_id);
CREATE INDEX idx_memory_category ON project_memory(category);

-- Repair Sessions
CREATE TABLE repair_sessions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,  -- PENDING, RUNNING, COMPLETED, FAILED
    config_json TEXT,  -- JSON of RepairLoopConfig
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);
CREATE INDEX idx_repair_job ON repair_sessions(job_id);
```

---

### V2__cost_tracking.sql (Additive — Cost Tracking Enhancement)
```sql
-- Already included in V1 as cost_aggregates, task_costs, daily_costs
-- This migration exists for historical tracking if cost tables added later
```

### V3__approval_tracking.sql (Additive — Approval System)
```sql
-- Already included in V1 as approvals, approval_audit
-- This migration exists for historical tracking if approval tables added later
```

### V4__multi_workspace.sql (Future — Phase 2+)
```sql
-- Workspaces
CREATE TABLE workspaces (
    id VARCHAR(128) NOT NULL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    description VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add workspace_id to existing tables
ALTER TABLE projects ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE jobs ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE project_memory ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE repair_sessions ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE rounds ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE findings ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE fixes ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE approvals ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);
ALTER TABLE approval_audit ADD COLUMN workspace_id VARCHAR(128) REFERENCES workspaces(id);

CREATE INDEX idx_projects_workspace ON projects(workspace_id);
CREATE INDEX idx_jobs_workspace ON jobs(workspace_id);
CREATE INDEX idx_memory_workspace ON project_memory(workspace_id);
CREATE INDEX idx_repair_workspace ON repair_sessions(workspace_id);
CREATE INDEX idx_rounds_workspace ON rounds(workspace_id);
CREATE INDEX idx_findings_workspace ON findings(workspace_id);
CREATE INDEX idx_fixes_workspace ON fixes(workspace_id);
CREATE INDEX idx_approvals_workspace ON approvals(workspace_id);
CREATE INDEX idx_audit_workspace ON approval_audit(workspace_id);
```

---

## 3. Entity Definitions (JPA)

### 3.1 Core Entities
```java
// Job.java
@Entity @Table(name = "jobs")
public record Job(
    @Id @Column(length = 64) String id,
    @Column(name = "project_id", length = 128) String projectId,
    @Enumerated(EnumType.STRING) @Column(length = 32) JobState state,
    int round,
    BigDecimal budgetUsd,
    BigDecimal costUsd,
    Instant createdAt,
    Instant finishedAt,
    String error
) {}

// Round.java
@Entity @Table(name = "rounds")
public record Round(
    @Id @Column(length = 64) String id,
    @Column(name = "job_id", length = 64) String jobId,
    @Column(name = "round_num") int roundNum,
    @Column(name = "build_ok") Boolean buildOk,
    @Column(name = "tests_passed") Integer testsPassed,
    @Column(name = "tests_failed") Integer testsFailed,
    @Column(precision = 5, scale = 2) BigDecimal coverage,
    @Column(name = "cost_usd", precision = 10, scale = 6) BigDecimal costUsd,
    @Column(name = "duration_sec") Integer durationSec,
    Instant startedAt,
    Instant finishedAt
) {}

// Finding.java
@Entity @Table(name = "findings")
public record Finding(
    @Id @Column(length = 64) String id,
    @Column(name = "job_id", length = 64) String jobId,
    @Column(name = "fingerprint", length = 64) String fingerprint,
    @Column(length = 64) String tool,
    @Column(name = "rule_id", length = 128) String ruleId,
    @Enumerated(EnumType.STRING) @Column(length = 16) Severity severity,
    @Column(length = 512) String file,
    Integer line,
    @Column(columnDefinition = "TEXT") String message,
    @Column(name = "first_seen_round") int firstSeenRound,
    @Column(name = "fixed_in_round") Integer fixedInRound,
    @Enumerated(EnumType.STRING) @Column(length = 16) FindingStatus status,
    Instant createdAt
) {}

// Fix.java
@Entity @Table(name = "fixes")
public record Fix(
    @Id @Column(length = 64) String id,
    @Column(name = "job_id", length = 64) String jobId,
    @Column(name = "finding_fingerprint", length = 64) String findingFingerprint,
    int round,
    @Column(columnDefinition = "TEXT") String summary,
    @Column(columnDefinition = "TEXT") String filesChanged,  // JSON array
    @Enumerated(EnumType.STRING) @Column(length = 16) FixResult result,
    @Column(name = "applied_at") Instant appliedAt
) {}

// Approval.java
@Entity @Table(name = "approvals", uniqueConstraints = @UniqueConstraint(columnNames = "job_id"))
public record Approval(
    @Id @Column(length = 64) String id,
    @Column(name = "job_id", length = 64) String jobId,
    @Enumerated(EnumType.STRING) @Column(length = 32) ApprovalStatus status,
    @Column(length = 128) String approver,
    @Column(length = 4000) String comment,
    @Column(name = "requested_at") Instant requestedAt,
    @Column(name = "decided_at") Instant decidedAt,
    @Column(name = "created_at") Instant createdAt
) {}

// ApprovalAudit.java
@Entity @Table(name = "approval_audit")
public record ApprovalAudit(
    @Id @Column(length = 64) String id,
    @Column(name = "approval_id", length = 64) String approvalId,
    @Enumerated(EnumType.STRING) @Column(length = 32) AuditAction action,
    @Column(length = 128) String actor,
    @Column(length = 4000) String comment,
    Instant timestamp
) {}

// TaskCost.java
@Entity @Table(name = "task_costs")
public record TaskCostEntity(
    @Id @Column(length = 64) String id,
    @Column(name = "task_id", length = 64) String taskId,
    @Column(length = 128) String model,
    @Column(length = 64) String provider,
    @Column(name = "prompt_tokens") long promptTokens,
    @Column(name = "completion_tokens") long completionTokens,
    @Column(name = "cost_usd_micro") long costUsdMicro,
    @Column(name = "start_time") Instant startTime,
    @Column(name = "last_update") Instant lastUpdate
) {}

// DailyCost.java
@Entity @Table(name = "daily_costs")
public record DailyCostEntity(
    @Id @Column(name = "date") LocalDate date,
    @Column(name = "prompt_tokens") long promptTokens,
    @Column(name = "completion_tokens") long completionTokens,
    @Column(name = "cost_usd_micro") long costUsdMicro,
    @Column(name = "updated_at") Instant updatedAt
) {}

// CostAggregate.java
@Entity @Table(name = "cost_aggregates")
public record CostAggregateEntity(
    @Id @Column(length = 64) String id,
    @Column(name = "total_prompt_tokens") long totalPromptTokens,
    @Column(name = "total_completion_tokens") long totalCompletionTokens,
    @Column(name = "total_cost_usd_micro") long totalCostUsdMicro,
    @Column(name = "daily_cost_usd_micro") long dailyCostUsdMicro,
    @Column(name = "last_reset") Instant lastReset,
    @Column(name = "updated_at") Instant updatedAt
) {}

// Project.java
@Entity @Table(name = "projects")
public record Project(
    @Id @Column(length = 128) String id,
    @Column(length = 256) String name,
    @Column(name = "root_path", length = 1024) String rootPath,
    @Column(name = "memory_path", length = 1024) String memoryPath,
    @Column(length = 32) String buildTool,
    @Column(length = 256) String testCommand,
    @Column(name = "environment_json", columnDefinition = "TEXT") String environmentJson,
    @Column(name = "created_at") Instant createdAt,
    @Column(name = "last_accessed_at") Instant lastAccessedAt
) {}

// ProjectMemory.java
@Entity @Table(name = "project_memory")
public record ProjectMemoryEntity(
    @Id @Column(length = 64) String id,
    @Column(name = "project_id", length = 128) String projectId,
    @Column(length = 32) String category,  -- CONVENTION, PATTERN, LEARNING, ERROR_PATTERN
    @Column(length = 256) String key,
    @Column(columnDefinition = "TEXT") String value,
    int priority,
    @Column(name = "created_at") Instant createdAt,
    @Column(name = "updated_at") Instant updatedAt
) {}

// RepairSession.java
@Entity @Table(name = "repair_sessions")
public record RepairSessionEntity(
    @Id @Column(length = 64) String id,
    @Column(name = "job_id", length = 64) String jobId,
    @Column(name = "task_id", length = 64) String taskId,
    @Column(length = 32) String status,  -- PENDING, RUNNING, COMPLETED, FAILED
    @Column(columnDefinition = "TEXT") String configJson,
    @Column(name = "started_at") Instant startedAt,
    @Column(name = "completed_at") Instant completedAt
) {}
```

---

## 4. Enums

```java
public enum JobState {
    CREATED, CLARIFYING, REQUIREMENTS_READY, PROMPT_REFINED, PLANNED,
    AWAITING_APPROVAL, BUILDING, VERIFYING, FIXING, COMPLETED, FAILED, CANCELLED
}

public enum Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
public enum FindingStatus { OPEN, FIXED, REGRESSED, ACCEPTED }
public enum FixResult { VERIFIED_FIXED, REVERTED, ACCEPTED, FAILED }
public enum ApprovalStatus { PENDING, APPROVED, REJECTED, TIMEOUT }
public enum AuditAction { REQUESTED, APPROVED, REJECTED, TIMEOUT, CANCELLED }
```

---

## 5. Repository Interfaces

```java
public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findByProjectIdOrderByCreatedAtDesc(String projectId);
    List<Job> findByStateIn(List<JobState> states);
    Optional<Job> findLatestByProjectId(String projectId);
}

public interface RoundRepository extends JpaRepository<Round, String> {
    List<Round> findByJobIdOrderByRoundNumAsc(String jobId);
    Optional<Round> findByJobIdAndRoundNum(String jobId, int roundNum);
}

public interface FindingRepository extends JpaRepository<Finding, String> {
    List<Finding> findByJobId(String jobId);
    List<Finding> findByJobIdAndStatus(String jobId, FindingStatus status);
    List<Finding> findByFingerprint(String fingerprint);
    long countByJobIdAndSeverity(String jobId, Severity severity);
}

public interface FixRepository extends JpaRepository<Fix, String> {
    List<Fix> findByJobId(String jobId);
    List<Fix> findByJobIdAndRound(String jobId, int round);
    List<Fix> findByFindingFingerprint(String fingerprint);
}

public interface ApprovalRepository extends JpaRepository<Approval, String> {
    Optional<Approval> findByJobId(String jobId);
}

public interface ApprovalAuditRepository extends JpaRepository<ApprovalAudit, String> {
    List<ApprovalAudit> findByApprovalIdOrderByTimestampAsc(String approvalId);
}

public interface TaskCostRepository extends JpaRepository<TaskCostEntity, String> {
    List<TaskCostEntity> findByTaskId(String taskId);
    List<TaskCostEntity> findByProvider(String provider);
}

public interface DailyCostRepository extends JpaRepository<DailyCostEntity, LocalDate> {
    Optional<DailyCostEntity> findById(LocalDate date);
    List<DailyCostEntity> findByDateBetween(LocalDate start, LocalDate end);
}

public interface CostAggregateRepository extends JpaRepository<CostAggregateEntity, String> {
    Optional<CostAggregateEntity> findById(String id);
}

public interface ProjectRepository extends JpaRepository<Project, String> {
    Optional<Project> findByName(String name);
    List<Project> findAllByOrderByLastAccessedAtDesc();
}

public interface ProjectMemoryRepository extends JpaRepository<ProjectMemoryEntity, String> {
    List<ProjectMemoryEntity> findByProjectId(String projectId);
    List<ProjectMemoryEntity> findByProjectIdAndCategory(String projectId, String category);
    List<ProjectMemoryEntity> findByProjectIdOrderByPriorityDescCreatedAtDesc(String projectId);
}

public interface RepairSessionRepository extends JpaRepository<RepairSessionEntity, String> {
    List<RepairSessionEntity> findByJobId(String jobId);
    Optional<RepairSessionEntity> findByJobIdAndTaskId(String jobId, String taskId);
}
```

---

## 6. Indexing Strategy

| Table | Index | Purpose |
|-------|-------|---------|
| jobs | `idx_jobs_project` (project_id) | List jobs by project |
| jobs | `idx_jobs_state` (state) | Filter by state |
| jobs | `idx_jobs_created` (created_at) | Time-range queries |
| rounds | `idx_rounds_job` (job_id) | Rounds for job |
| rounds | `idx_rounds_num` (job_id, round_num) | Specific round lookup |
| findings | `idx_findings_job` (job_id) | Findings for job |
| findings | `idx_findings_fingerprint` | Deduplication across rounds |
| findings | `idx_findings_severity` | Filter by severity |
| findings | `idx_findings_status` | Filter OPEN/FIXED |
| fixes | `idx_fixes_job` (job_id) | Fixes for job |
| fixes | `idx_fixes_fingerprint` | Track fix history per finding |
| approvals | `idx_approvals_job` (job_id) | Unique per job |
| approvals | `idx_approvals_status` | Pending approvals |
| approval_audit | `idx_audit_approval` | Audit trail per approval |
| task_costs | `idx_task_cost_task` (task_id) | Costs per task |
| task_costs | `idx_task_cost_provider` | Costs by provider |

---

## 7. Migration Strategy

### 7.1 Versioning Rules
- **Never edit applied migrations** — create new version
- **Version format**: `V{number}__{description}.sql`
- **Baseline**: `V1__initial_schema.sql`
- **Production**: `validate` mode (fail on drift)
- **Test**: `create-drop` (clean slate each run)

### 7.2 Migration Checklist (Per Release)
- [ ] New migration file in `src/main/resources/db/migration/`
- [ ] Version number increments sequentially
- [ ] Idempotent where possible (IF NOT EXISTS)
- [ ] Rollback plan documented (or `DOWN` migration for complex changes)
- [ ] Tested against H2 (test) + SQLite (prod)

---

## 7. Data Access Patterns

### 7.1 Common Queries
```java
// Job with all rounds + findings (for report generation)
@Query("SELECT j FROM Job j LEFT JOIN FETCH j.rounds r LEFT JOIN FETCH r.findings WHERE j.id = :id")
Optional<Job> findWithFullDetail(String id);

// Active jobs needing attention
@Query("SELECT j FROM Job j WHERE j.state IN :states AND j.costUsd < j.budgetUsd")
List<Job> findActiveJobsNeedingAttention(List<JobState> states);

// Findings by severity for dashboard
@Query("SELECT f FROM Finding f WHERE f.jobId = :jobId AND f.severity IN :severities")
List<Finding> findByJobAndSeverities(String jobId, List<Severity> severities);

// Cost tracking
@Query("SELECT SUM(c.costUsdMicro) FROM TaskCostEntity c WHERE c.taskId = :taskId")
Long getTaskCostMicro(String taskId);
```

---

## 8. Migration Execution Order

```text
V1__initial_schema.sql          (baseline - all core tables)
V2__cost_tracking.sql           (cost tracking tables - if split)
V3__approval_tracking.sql       (approval + audit tables)
V4__multi_workspace.sql         (workspaces - v2+)
```

**Run Order**: V1 → V2 → V3 → V4 (Flyway handles ordering by version)

---

*This database design supports the v1 MVP with room for v1.1+ extensions. All schema changes go through Flyway migrations only.*