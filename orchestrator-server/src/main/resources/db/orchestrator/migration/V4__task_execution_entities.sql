-- Orchestrator Database Schema
-- Version 4: Task Execution Entities

-- Task execution states (for execution engine)
CREATE TABLE task_executions (
    task_id VARCHAR(64) NOT NULL PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    request TEXT NOT NULL,
    requester VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json TEXT,
    tool_results_json TEXT,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_task_execution_project ON task_executions(project_id);
CREATE INDEX idx_task_execution_status ON task_executions(status);
CREATE INDEX idx_task_execution_created ON task_executions(created_at);

-- Task execution rounds (for retry tracking)
CREATE TABLE task_execution_rounds (
    id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(64) NOT NULL REFERENCES task_executions(task_id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json TEXT,
    tool_results_json TEXT,
    error TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    UNIQUE (task_id, round_number)
);

CREATE INDEX idx_round_task ON task_execution_rounds(task_id);
CREATE INDEX idx_round_status ON task_execution_rounds(status);

-- Task execution tools (for audit trail)
CREATE TABLE task_execution_tools (
    id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(64) NOT NULL REFERENCES task_executions(task_id) ON DELETE CASCADE,
    round_id UUID REFERENCES task_execution_rounds(id) ON DELETE SET NULL,
    tool_name VARCHAR(64) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_task ON task_execution_tools(task_id);
CREATE INDEX idx_tool_round ON task_execution_tools(round_id);
CREATE INDEX idx_tool_executed ON task_execution_tools(executed_at);

-- Tool call records (for audit trail)
CREATE TABLE tool_calls (
    id UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(64) NOT NULL REFERENCES task_executions(task_id) ON DELETE CASCADE,
    round_id UUID REFERENCES task_execution_rounds(id) ON DELETE SET NULL,
    tool_name VARCHAR(128) NOT NULL,
    input_json TEXT,
    output_json TEXT,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tool_call_task ON tool_calls(task_id);
CREATE INDEX idx_tool_call_executed ON tool_calls(executed_at);

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