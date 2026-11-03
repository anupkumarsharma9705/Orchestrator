-- Orchestrator Database Schema
-- Version 7: Engine Store tables (jobs, rounds, findings, costs)
--
-- The engine-store module contributes JPA entities for job execution history
-- (ArtifactStore/StateManager). Its own migrations are SQLite-oriented and
-- live in the same db/migration prefix, so the server owns the PostgreSQL
-- version of those tables here.

CREATE TABLE IF NOT EXISTS jobs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    total_cost_usd NUMERIC(10, 6) DEFAULT 0,
    total_duration_sec INTEGER DEFAULT 0,
    config_json TEXT
);

CREATE TABLE IF NOT EXISTS rounds (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    round_num INTEGER NOT NULL,
    build_ok BOOLEAN NOT NULL DEFAULT FALSE,
    tests_passed INTEGER NOT NULL DEFAULT 0,
    tests_failed INTEGER NOT NULL DEFAULT 0,
    coverage NUMERIC(5, 2) DEFAULT 0,
    cost_usd NUMERIC(10, 6) DEFAULT 0,
    duration_sec INTEGER DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    metadata_json TEXT
);

CREATE TABLE IF NOT EXISTS findings (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    round_id VARCHAR(64) NOT NULL REFERENCES rounds(id) ON DELETE CASCADE,
    fingerprint VARCHAR(64) NOT NULL,
    tool VARCHAR(100) NOT NULL,
    rule_id VARCHAR(255) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    file_path VARCHAR(500),
    line_number INTEGER DEFAULT 0,
    message TEXT NOT NULL,
    code_snippet TEXT,
    status VARCHAR(20) NOT NULL,
    first_seen_round INTEGER NOT NULL,
    fixed_in_round INTEGER,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS costs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    round_id VARCHAR(64) REFERENCES rounds(id) ON DELETE SET NULL,
    task_id VARCHAR(64),
    profile VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INTEGER NOT NULL,
    output_tokens INTEGER NOT NULL,
    cost_usd NUMERIC(10, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rounds_job_id ON rounds(job_id);
CREATE INDEX IF NOT EXISTS idx_findings_round_id ON findings(round_id);
CREATE INDEX IF NOT EXISTS idx_findings_fingerprint ON findings(fingerprint);
CREATE INDEX IF NOT EXISTS idx_findings_status ON findings(status);
CREATE INDEX IF NOT EXISTS idx_costs_job_id ON costs(job_id);
CREATE INDEX IF NOT EXISTS idx_costs_round_id ON costs(round_id);
