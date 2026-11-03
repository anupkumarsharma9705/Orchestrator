# Decisions

## ADR-001: Modular Monolith Architecture
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need to build AI-assisted software engineering control plane
**Decision**: Start with modular Spring Boot monolith rather than microservices
**Rationale**: 
- Simpler deployment and debugging
- Clear module boundaries within single deployment
- Can extract services later if needed
- Avoids distributed system complexity prematurely

## ADR-002: Spring AI with OpenRouter
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need LLM integration for code generation, review, planning
**Decision**: Use Spring AI with OpenRouter as primary provider
**Rationale**: 
- Spring AI provides abstraction over providers
- OpenRouter gives access to multiple models
- Configuration-driven model selection
- Can add Ollama, Kaggle later without code changes

## ADR-003: Permission-Based Tool Execution
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need to prevent AI from performing dangerous operations
**Decision**: Enforce permissions at ToolRegistry.execute() with 4 risk levels
**Rationale**:
- Centralized enforcement point
- Model cannot bypass by crafting prompts
- Clear audit trail of permission decisions
- Extensible for new risk levels

## ADR-004: Structured AI Output (JSON)
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need reliable parsing of AI responses
**Decision**: Require structured JSON output with schema validation
**Rationale**:
- Eliminates markdown parsing fragility
- Enables schema validation
- Enables type-safe deserialization
- Fail fast on malformed output

## ADR-005: Bounded Repair Loop
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need automated repair with safety guarantees
**Decision**: Implement bounded repair loop with max attempts, timeout, human approval
**Rationale**:
- Prevents infinite loops
- Human remains in control
- Clear audit trail of repair attempts
- Configurable limits per project

## ADR-006: Project-Scoped Memory
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Multiple projects need isolated AI memory
**Decision**: Each project has `.ai/` directory with 14 standard memory files
**Rationale**:
- Isolation prevents cross-project contamination
- Standard structure enables tooling
- Filesystem-based (no DB required initially)
- Git-trackable (except sensitive files)

## ADR-007: Deterministic Context Selection
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Cannot send entire repository to model
**Decision**: Deterministic context selection pipeline (dir scan -> keyword search -> imports -> memory)
**Rationale**:
- Predictable, debuggable behavior
- No vector DB dependency initially
- Can add RAG later as enhancement
- Token budget control

## ADR-007: Human-in-the-Loop Approval
**Date**: 2026-09-22
**Status**: Accepted
**Context**: AI must not deploy/push without human consent
**Decision**: Explicit approval gates via REST API before write operations and releases
**Rationale**:
- Legal/compliance requirement
- Prevents accidental destructive actions
- Audit trail of human decisions
- Async approval via REST (not blocking CLI)

## ADR-008: Tool Risk Levels
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Different tools have different risk profiles
**Decision**: 4-tier risk classification (SAFE, CONTROLLED, APPROVAL_REQUIRED, HUMAN_ONLY)
**Rationale**:
- Granular control over AI capabilities
- Clear escalation path
- Enables progressive capability rollout
- Aligns with security best practices

## ADR-008: Artifact Persistence
**Date**: 2026-09-22
**Status**: Accepted
**Context**: Need to persist repair attempts, verification results, diffs
**Decision**: Filesystem-based ArtifactStore with UUID naming
**Rationale**:
- Simple, no external dependencies
- UUID prevents collisions
- Metadata in separate index
- Can migrate to S3/DB later