-- V1: Initial schema for job execution tracking
-- SQLite compatible

CREATE TABLE jobs (
    id TEXT PRIMARY KEY,
    project_name TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    created_at TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT,
    total_cost_usd REAL DEFAULT 0.0,
    total_duration_sec INTEGER DEFAULT 0,
    config_json TEXT
);

CREATE TABLE rounds (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    round_num INTEGER NOT NULL,
    build_ok BOOLEAN NOT NULL,
    tests_passed INTEGER NOT NULL DEFAULT 0,
    tests_failed INTEGER NOT NULL DEFAULT 0,
    coverage REAL DEFAULT 0.0,
    cost_usd REAL DEFAULT 0.0,
    duration_sec INTEGER DEFAULT 0,
    started_at TEXT NOT NULL,
    finished_at TEXT NOT NULL,
    metadata_json TEXT
);

CREATE TABLE findings (
    id TEXT PRIMARY KEY,
    round_id TEXT NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    fingerprint TEXT NOT NULL,
    tool TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    file_path TEXT,
    line_number INTEGER DEFAULT 0,
    message TEXT NOT NULL,
    code_snippet TEXT,
    status TEXT NOT NULL CHECK (status IN ('OPEN', 'FIXED', 'REGRESSED', 'ACCEPTED', 'NEW')),
    first_seen_round INTEGER NOT NULL,
    fixed_in_round INTEGER,
    created_at TEXT NOT NULL
);

CREATE TABLE costs (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    round_id TEXT REFERENCES rounds(id) ON DELETE SET NULL,
    task_id TEXT,
    profile TEXT NOT NULL,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    cost_usd REAL NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_rounds_job_id ON rounds(job_id);
CREATE INDEX idx_findings_round_id ON findings(round_id);
CREATE INDEX idx_findings_fingerprint ON findings(fingerprint);
CREATE INDEX idx_findings_status ON findings(status);
CREATE INDEX idx_costs_job_id ON costs(job_id);
CREATE INDEX idx_costs_round_id ON costs(round_id);