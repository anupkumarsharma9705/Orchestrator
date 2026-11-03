-- Orchestrator Database Schema
-- Version 2: Cost Tracking

CREATE TABLE task_costs (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    provider VARCHAR(64),
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    start_time TIMESTAMPTZ NOT NULL,
    last_update TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_task_cost_task ON task_costs(task_id);
CREATE INDEX idx_task_cost_model ON task_costs(model);
CREATE INDEX idx_task_cost_provider ON task_costs(provider);
CREATE INDEX idx_task_cost_start_time ON task_costs(start_time);
CREATE INDEX idx_task_cost_last_update ON task_costs(last_update);

CREATE TABLE daily_costs (
    date DATE NOT NULL PRIMARY KEY,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_daily_cost_date ON daily_costs(date);

CREATE TABLE cost_aggregates (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    total_prompt_tokens BIGINT DEFAULT 0,
    total_completion_tokens BIGINT DEFAULT 0,
    total_cost_usd_micro BIGINT DEFAULT 0,
    daily_cost_usd_micro BIGINT DEFAULT 0,
    last_reset TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Insert default aggregate record
INSERT INTO cost_aggregates (id, total_prompt_tokens, total_completion_tokens, total_cost_usd_micro, daily_cost_usd_micro, last_reset, updated_at)
VALUES ('default', 0, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);