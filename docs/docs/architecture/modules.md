---
sidebar_position: 7
title: Architecture - Modules
---

# Architecture: Modules

## Module Overview

The reactor contains 11 modules:

| Module | Description | Key Exports |
|--------|-------------|-------------|
| `engine-model` | Core DTOs, entities, enums | `ChatRequest`, `ChatResponse`, `Plan`, `Task`, `Project` |
| `engine-llm` | LLM abstraction, routing, cost tracking | `ModelRouter`, `ModelProvider`, `CostTracker` |
| `engine-core` | Generation pipeline | `Clarifier`, `Planner`, `Coder`, `Fixer`, `Refiner`, `StateManager` |
| `engine-templates` | Spring Boot template rendering | `TemplateEngine` |
| `engine-sandbox` | Docker sandbox execution | `SandboxService`, `SandboxConfig` |
| `engine-verify` | Verification & scanning runners | `VerificationRunner`, `ScannerRegistry` |
| `engine-store` | Persistence layer (PostgreSQL) | `ArtifactStore`, `CostTracker` (persisted) |
| `eval` | Evaluation & benchmarking | `EvaluationRunner` |
| `orchestrator-api-contract` | Shared API contract definitions | Contract DTOs |
| `orchestrator-server` | REST API server | Controllers, Security, Auth |
| `app-cli` | CLI interface | `CompanionCli`, Commands |

## Module Dependencies

```
app-cli
    │
    ├── orchestrator-server (REST client)
    └── engine-templates (offline mode)
    
orchestrator-server
    ├── engine-core
    ├── engine-llm
    ├── engine-sandbox
    ├── engine-verify
    ├── engine-store
    └── engine-model

engine-core
    ├── engine-model
    └── engine-llm (for LLM calls)

engine-llm
    └── engine-model
```

## Module Communication

- **Sync**: Direct method calls within JVM (engine-core, engine-llm, engine-model)
- **Async**: REST API (app-cli → orchestrator-server)
- **Async**: Message queues for long-running tasks (future)
- **Events**: Domain events for audit logging

## Adding a New Module

1. Create module directory with standard Maven structure
2. Add to parent `pom.xml` `<modules>` section
3. Define dependencies in module's `pom.xml`
4. Add module to parent `dependencyManagement` if needed
5. Export public API via `module-info.java` (future)
6. Add integration tests