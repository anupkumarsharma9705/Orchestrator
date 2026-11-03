-- Orchestrator Database Schema
-- Version 3: Approval Tracking

CREATE TABLE approvals (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    approver VARCHAR(128),
    comment VARCHAR(4000),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_approval_task ON approvals(task_id);
CREATE INDEX idx_approval_status ON approvals(status);
CREATE INDEX idx_approval_requested ON approvals(requested_at);

CREATE TABLE approval_audit (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    comment VARCHAR(4000),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_approval_audit_approval ON approval_audit(approval_id);
CREATE INDEX idx_approval_audit_timestamp ON approval_audit(occurred_at);