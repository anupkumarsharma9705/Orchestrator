/**
 * Core data model for the Java Companion Orchestrator.
 * 
 * This package contains pure Java records (no dependencies) that represent
 * the core data structures used throughout the orchestration pipeline.
 * 
 * All records are:
 * - Immutable
 * - Serializable (Jackson JSON)
 * - Validated (Jakarta Bean Validation)
 * - Equatable (proper equals/hashCode based on ID)
 * 
 * Modules:
 * - Requirements: Requirement, RequirementsDoc, Clarification
 * - Planning: PlanTask, Plan, TaskStatus
 * - Verification: Finding, Fix, Severity, FindingStatus, FixResult
 * - Execution: Job, Round, RoundReport, JobState
 * - Cost Tracking: TaskCost, DailyCost, CostAggregate
 * - Approval: Approval, ApprovalAudit, ApprovalStatus, AuditAction
 * - Project: Project, ProjectMemory, RepairSession
 * - Utilities: FingerprintUtil, Priority
 */
package com.example.orchestrator.model;