---
sidebar_position: 7
title: Architecture Overview
---

# Architecture Overview

> **Implementation status:** the current server persists state in PostgreSQL and
> writes audit events to a local file. Redis, S3/object storage, and Sentry are
> planned, not implemented. Semgrep and Trivy scanners exist in `engine-verify`
> and the sandbox image, but root CI runs only `mvn verify` and Gitleaks.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         companion.jar                                │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────────┐ │
│  │   CLI       │  │     ENGINE       │  │       TOOLS            │ │
│  │  (picocli)  │  │   (Library)      │  │     (20+)              │ │
│  └──────┬──────┘  └────────┬─────────┘  └───────────┬────────────┘ │
│         │                   │                        │              │
│    ┌────┴────┐      ┌───────┴───────┐       ┌────────┴────────┐   │
│    │Clarifier│      │  ModelRouter  │       │ File/Build/Scan   │   │
│    │ Refiner │      │  (Providers)  │       │ Fix/Verify/Sandbox│   │
│    │ Planner │      │  CostTracker  │       │                   │   │
│    │ Coder   │      │  CircuitBrkr  │       │                   │   │
│    │ Fixer   │      │               │       │                   │   │
│    └─────────┘      └───────────────┘       └───────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                       ┌─────────┴─────────┐
                       ▼                   ▼
               ┌───────────────┐   ┌─────────────────┐
               │ PostgreSQL    │   │  Sandbox        │
               │  (Jobs, Rounds,│   │  (Docker)       │
               │   Findings)   │   │  Build/Test/    │
               │               │   │  Scan/Fix       │
               └───────────────┘   └─────────────────┘
```

## Module Structure

| Module | Responsibility | Key Classes |
|--------|---------------|-------------|
| **engine-model** | Core DTOs, entities, enums | `ChatRequest`, `ChatResponse`, `Plan`, `Task` |
| **engine-llm** | LLM abstraction, routing, cost | `ModelRouter`, `ModelProvider`, `CostTracker` |
| **engine-core** | Generation pipeline | `Clarifier`, `Planner`, `Coder`, `Fixer`, `Refiner` |
| **engine-templates** | Template rendering | `TemplateEngine` |
| **engine-sandbox** | Docker sandbox | `SandboxService`, `SandboxConfig` |
| **engine-verify** | Verification runners | `VerificationRunner`, `ScannerRegistry` |
| **engine-store** | Persistence | `ArtifactStore`, `CostTracker` (persisted) |
| **eval** | Evaluation framework | `EvaluationRunner` |
| **orchestrator-api-contract** | Shared API contract | Contract DTOs |
| **orchestrator-server** | REST API server | Controllers, Security, Auth |
| **app-cli** | CLI interface | `CompanionCli`, Commands |

## Data Flow

### Online Mode (Full Pipeline)

```
User Input (description)
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 1. CLARIFIER — Interactive Q&A                                 │
│    • Generates 3-8 clarifying questions                        │
│    • User answers with defaults/help                           │
│    • Produces RequirementsDoc                                  │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 2. PLANNER — Creates Implementation Plan                       │
│    • 5-15 tasks with dependencies                              │
│    • Each task: title, files, description, acceptance criteria │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 3. REFINER — Plan validation & improvement                     │
│    • Validates against security policies                       │
│    • Checks for best practices                                 │
│    • Suggests improvements                                     │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 4. PLAN REVIEW — Human approval                                │
│    • Interactive table of tasks                                │
│    • Approve / Reject / View details                           │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 5. CODER — Code generation per task                            │
│    • Template rendering + LLM completion                       │
│    • Writes files to sandbox workspace                         │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 6. VERIFICATION — Build, Test, Scan                            │
│    • mvn compile → test → verify                               │
│    • Scanners available: SpotBugs, Checkstyle, Semgrep,        │
│      Trivy, OWASP Dependency-Check, secrets                    │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ 7. FIXER — Auto-fix failures (up to 5 rounds)                  │
│    • Analyze errors → Generate fixes → Rebuild                 │
│    • Max 5 rounds until green or max reached                   │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
   DONE — Artifacts ready
```

### Offline Mode (Simplified)

```
User Description
       │
       ▼
┌────────────────────────────────────────────────────────────────┐
│ TemplateEngine (local)                                         │
│  • Applies project name and base package                      │
│  • Renders a secure Spring Boot skeleton                      │
│  • Does not parse the description or generate domain code     │
└────────────────────────────────────────────────────────────────┘
       │
       ▼
   Skeleton project (no LLM calls)
```

## Core Components

### ModelRouter (`engine-llm`)

Routes requests to LLM providers with:
- **Circuit Breaker** — Per-provider failure tracking
- **Cost Tracking** — Per-task, per-day budgets with alerts
- **Failover** — Automatic failover on failures
- **Provider Abstraction** - NVIDIA (default), OpenRouter, Ollama (Kaggle is a disabled stub)

### Clarifier (`engine-core`)

Interactive clarification engine:
- Generates context-aware questions
- Supports single-choice, multi-choice, yes/no, free-text
- Non-interactive mode with defaults

### Planner (`engine-core`)

Generates implementation plans:
- Task decomposition with dependencies
- Acceptance criteria per task
- File-level granularity

### Coder (`engine-core`)

Code generation per task:
- Template + LLM completion hybrid
- Writes to sandbox workspace
- Tracks file changes per task

### Fixer (`engine-core`)

Auto-fix engine:
- Error classification (compile, test, security, style)
- Fix generation via LLM
- Patch application with validation
- Max 5 rounds

### SandboxService (`engine-sandbox`)

Secure Docker execution:
- **Isolation:** Read-only rootfs, dropped capabilities, no network
- **Resources:** Memory (2GB), CPU (2 cores), Processes (100)
- **Workspace:** Validated path boundaries
- **Maven Cache:** Shared volume with isolation
- **Logs:** Full stdout/stderr capture

### TemplateEngine (`engine-templates`)

Spring Boot template rendering:
- Application entry point and package layout
- Security config (JWT filter, CORS policy)
- PostgreSQL/Flyway configuration
- Checkstyle/SpotBugs configuration
- Dockerfile, docker-compose, CI/CD
- Doc-only `package-info` placeholders for domain packages

## Security Architecture

### Defense in Depth

| Layer | Mechanism |
|-------|-----------|
| **Network** | mTLS between CLI ↔ Server, no sandbox egress |
| **Container** | Dropped caps, read-only rootfs, no-new-privileges |
| **Application** | JWT auth, RBAC, input validation, size limits |
| **Code** | ReDoS protection, secret scanning, static analysis |
| **Supply Chain** | Image validation, artifact checksums, Gitleaks CI |

### Threat Model

| Threat | Mitigation |
|--------|------------|
| Prompt injection | PromptSanitizer + input size limits |
| Code execution | Container isolation, dropped capabilities, no network |
| Path traversal | Workspace boundary validation |
| Resource exhaustion | Container limits, timeouts |
| Supply chain | Image allowlist, artifact checksums |

## Scalability

| Component | Scaling Strategy |
|-----------|------------------|
| Server | Single trusted instance; multi-node deployment not tested |
| Sandbox | Per-request container when Docker is available |
| Database | PostgreSQL |
| LLM Providers | Circuit breaker + failover |
| Artifacts | Local files under `orchestrator.store.base-path` + metadata in PostgreSQL |

## Monitoring & Observability

| Metric | Source |
|--------|--------|
| Request latency | Server filters + actuator |
| Error rates | Structured audit logs |
| LLM costs | CostTracker (per-task, daily) |
| Provider health | `/api/health/providers` |
| Sandbox metrics | Duration, exit codes, OOM |
| Rate limiting | Resilience4j metrics |

## Deployment Architecture

### Development
```
Local: companion.jar + Docker Desktop
```

### Production

No multi-node topology has been validated. A single server with a managed
PostgreSQL instance and persistent storage for the audit log and artifacts is
the supported shape today.

```
        CLI (mTLS + API key)
                 │
                 ▼
        ┌─────────────────┐
        │ Orchestrator    │
        │ server          │
        └────────┬────────┘
                 │
        ┌────────┴────────┐
        ▼                 ▼
  ┌───────────┐   ┌──────────────┐
  │PostgreSQL │   │Audit log +   │
  │           │   │artifacts     │
  └───────────┘   └──────────────┘
```

---

## Next Steps

- [Modules](/docs/architecture/modules) — Deep dive into each module
- [Security](/docs/architecture/security) — Security controls
- [Deployment](/docs/architecture/deployment) — Deployment guidance