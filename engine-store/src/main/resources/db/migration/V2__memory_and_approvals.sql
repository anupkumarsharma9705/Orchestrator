-- V2: Add project memory and approval tables

CREATE TABLE project_memory (
    id TEXT PRIMARY KEY,
    project_name TEXT NOT NULL UNIQUE,
    memory_key TEXT NOT NULL,
    memory_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE approvals (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    stage TEXT NOT NULL CHECK (stage IN ('REQUIREMENTS', 'PLAN', 'GENERATION', 'FIX_LOOP')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_at TEXT NOT NULL,
    decided_at TEXT,
    decided_by TEXT,
    feedback TEXT
);

CREATE INDEX idx_project_memory_project ON project_memory(project_name);
CREATE INDEX idx_approvals_job_id ON approvals(job_id);