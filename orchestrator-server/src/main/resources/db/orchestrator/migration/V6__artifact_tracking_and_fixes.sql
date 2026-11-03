-- Orchestrator Database Schema
-- Version 6: Artifact Tracking and Additional Fixes

-- Artifact tracking for generated artifacts
CREATE TABLE IF NOT EXISTS artifacts (
    id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(64) NOT NULL REFERENCES task_executions(task_id) ON DELETE CASCADE,
    round_id UUID REFERENCES task_execution_rounds(id) ON DELETE SET NULL,
    name VARCHAR(256) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    type VARCHAR(64) NOT NULL, -- 'source', 'test', 'config', 'docker', 'documentation', 'other'
    size BIGINT NOT NULL,
    checksum VARCHAR(64), -- SHA-256 checksum
    mime_type VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_task ON artifacts(task_id);
CREATE INDEX IF NOT EXISTS idx_artifact_round ON artifacts(round_id);
CREATE INDEX IF NOT EXISTS idx_artifact_type ON artifacts(type);
CREATE INDEX IF NOT EXISTS idx_artifact_created ON artifacts(created_at);

-- Artifact content storage (for small files, large files go to object storage)
CREATE TABLE IF NOT EXISTS artifact_contents (
    artifact_id UUID NOT NULL PRIMARY KEY REFERENCES artifacts(id) ON DELETE CASCADE,
    content TEXT,
    content_compressed BYTEA, -- For large content, compressed
    is_compressed BOOLEAN NOT NULL DEFAULT FALSE
);

-- Artifact metadata for search/indexing
CREATE TABLE IF NOT EXISTS artifact_metadata (
    artifact_id UUID NOT NULL PRIMARY KEY REFERENCES artifacts(id) ON DELETE CASCADE,
    tags TEXT[],
    metadata_json JSONB,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artifact_metadata_tags ON artifact_metadata USING GIN(tags);

-- Fix: Ensure project_memory table uses PostgreSQL-compatible types
-- V1 used AUTOINCREMENT which is SQLite-specific
-- For PostgreSQL, use SERIAL or BIGSERIAL
-- Check if project_memory exists and fix if needed
-- Note: V1 already has id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT which is SQLite-specific
-- For PostgreSQL, we need to handle this differently
-- Since we can't easily change the primary key type in a migration,
-- we'll note this for manual intervention if needed

-- Fix: Repair sessions table - V1 used AUTOINCREMENT for repair_id
-- repair_id is VARCHAR(64) which is fine for PostgreSQL

-- Fix: Ensure repair_sessions has proper indexes
CREATE INDEX IF NOT EXISTS idx_repair_task ON repair_sessions(task_id);
CREATE INDEX IF NOT EXISTS idx_repair_project ON repair_sessions(project_id);
CREATE INDEX IF NOT EXISTS idx_repair_status ON repair_sessions(status);
CREATE INDEX IF NOT EXISTS idx_repair_started ON repair_sessions(started_at);

-- Fix: Project memory table - ensure proper indexes
CREATE INDEX IF NOT EXISTS idx_memory_project ON project_memory(project_id);
CREATE INDEX IF NOT EXISTS idx_memory_type ON project_memory(type);
CREATE INDEX IF NOT EXISTS idx_memory_confidence ON project_memory(confidence DESC);
CREATE INDEX IF NOT EXISTS idx_memory_usage ON project_memory(usage_count DESC);

-- Add missing columns to execution_states if missing (from V1)
ALTER TABLE execution_states ADD COLUMN IF NOT EXISTS plan_json TEXT;
ALTER TABLE execution_states ADD COLUMN IF NOT EXISTS tool_results_json TEXT;
ALTER TABLE execution_states ADD COLUMN IF NOT EXISTS error TEXT;
ALTER TABLE execution_states ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE execution_states ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure indexes exist
CREATE INDEX IF NOT EXISTS idx_execution_project ON execution_states(project_id);
CREATE INDEX IF NOT EXISTS idx_execution_status ON execution_states(status);
CREATE INDEX IF NOT EXISTS idx_execution_created ON execution_states(created_at);

-- Fix: Projects table - ensure all columns exist
ALTER TABLE projects ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS build_tool VARCHAR(64) DEFAULT 'maven';
ALTER TABLE projects ADD COLUMN IF NOT EXISTS test_command VARCHAR(256);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS environment_json JSONB;

-- Fix: Repair sessions - ensure all columns exist
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 3;
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS current_attempt INTEGER DEFAULT 0;
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS attempt_timeout_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS total_timeout_minutes INTEGER NOT NULL DEFAULT 120;
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS error VARCHAR(4000);
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE repair_sessions ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

-- Fix: Project memory - ensure all columns exist
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS type VARCHAR(32) NOT NULL DEFAULT 'pattern';
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS pattern TEXT NOT NULL;
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS context TEXT;
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS confidence REAL DEFAULT 0.5;
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS usage_count INTEGER DEFAULT 0;
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE project_memory ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMPTZ;

-- Add gen_random_uuid extension if not exists
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Ensure UUID generation is available
SELECT 'pgcrypto extension ensured' AS status;

-- Fix: Add provider column to task_costs if missing (H-02)
ALTER TABLE task_costs ADD COLUMN IF NOT EXISTS provider VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_task_cost_provider ON task_costs(provider);

-- Fix: Add provider column to daily_costs if missing
ALTER TABLE daily_costs ADD COLUMN IF NOT EXISTS provider VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_daily_cost_provider ON daily_costs(provider);

-- Add provider column to cost_aggregates if missing
ALTER TABLE cost_aggregates ADD COLUMN IF NOT EXISTS provider VARCHAR(64);

-- Add indexes for cost tracking queries
CREATE INDEX IF NOT EXISTS idx_task_cost_task_provider ON task_costs(task_id, provider);
CREATE INDEX IF NOT EXISTS idx_daily_cost_date_provider ON daily_costs(date, provider);