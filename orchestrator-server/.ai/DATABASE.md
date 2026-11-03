# Database

## Current State
No database configured. All state is in-memory or filesystem-based.

## Future: PostgreSQL Schema

### Projects Table
```sql
CREATE TABLE projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    root_path TEXT NOT NULL,
    memory_path TEXT NOT NULL,
    build_tool VARCHAR(50) DEFAULT 'maven',
    test_command VARCHAR(100) DEFAULT 'test',
    environment JSONB DEFAULT '{}',
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Jobs/Tasks Table
```sql
CREATE TABLE jobs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id),
    request TEXT NOT NULL,
    requester VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    plan JSONB,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Attempts Table
```sql
CREATE TABLE attempts (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES jobs(id),
    attempt_number INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    steps JSONB,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Approvals Table
```sql
CREATE TABLE approvals (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES jobs(id),
    decision VARCHAR(20) NOT NULL,
    approver VARCHAR(255) NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Artifacts Table
```sql
CREATE TABLE artifacts (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) REFERENCES jobs(id),
    attempt_id VARCHAR(64) REFERENCES attempts(id),
    name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Audit Log Table
```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_type VARCHAR(100) NOT NULL,
    data JSONB NOT NULL
);
```

### Indices
```sql
CREATE INDEX idx_jobs_project_id ON jobs(project_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_attempts_job_id ON attempts(job_id);
CREATE INDEX idx_approvals_job_id ON approvals(job_id);
CREATE INDEX idx_artifacts_job_id ON artifacts(job_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
```

## Migration Strategy
- Use Flyway for schema migrations
- Versioned migration files in `src/main/resources/db/migration/`
- Baseline migration for initial schema
- Incremental migrations for schema changes

## Connection Pool
- HikariCP (Spring Boot default)
- Pool size: 10 (configurable)
- Connection timeout: 30s
- Idle timeout: 10min