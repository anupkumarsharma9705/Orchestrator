# Development Progress
## Java Companion Orchestrator — v1 CLI MVP

---

## Phase 0: Foundation (Weeks 1-2)

| Task | Status | Notes |
|------|--------|-------|
| Install Java 21, Maven, Docker, Git, IntelliJ | ✅ Done | Already installed |
| Build Spring Boot CRUD API with tests | ✅ Done | Existing codebase |
| Create GitHub repo + multi-module layout + CI | 🔄 In Progress | Need to create multi-module structure |
| First LLM call from Java (Spring AI) | ✅ Done | Existing codebase has ModelRouter |
| Data model records + JSON round-trip tests | ✅ Done | **Step 1.1 Complete** |
| FakeModel + ModelRouter with failover test | ✅ Done | **Step 1.2 Complete** |
| Clarifier for "REST CRUD for Book" → requirements.md | ⏳ Pending | Step 1.3 |
| Review diary, fix loose ends, tag v0.0.1 | ⏳ Pending | Week 2 |

---

## Phase 1: Core Engine (Weeks 3-38)

### Step 1.1: Data Model (engine-model) — Week 3
| Task | Status | Notes |
|------|--------|-------|
| Create records: Requirement, RequirementsDoc, PlanTask, Plan, Finding, Fix, RoundReport, Job | ✅ Done | All records created with JSON round-trip tests |
| Enums: Severity, Status, JobState, FixResult, ApprovalStatus, AuditAction | ✅ Done | All enums created |
| Fingerprint utility: `hash(tool + "|" + ruleId + "|" + filePath + "|" + codeLine)` | ✅ Done | FingerprintUtil with tests |
| JSON round-trip tests for all records | ✅ Done | 16 tests passing |
| Unit tests for all model classes | ✅ Done | 28 tests passing (12 FingerprintUtil + 16 ModelRecords) |

### Step 1.2: LLM Layer (engine-llm) — Weeks 4-5
| Task | Status | Notes |
|------|--------|-------|
| Spring AI config: Ollama, NVIDIA, OpenRouter providers | ✅ Done | Providers implemented with auto-configuration |
| ModelRouter: primary + fallbacks + circuit breaker | ✅ Done | ModelRouter with circuit breaker, failover, cost tracking |
| Profiles: PLANNER, CODER, REVIEWER, DOCUMENTATION | ✅ Done | Config-driven profiles with fallback chains |
| CostTracker: tokens → cost via price table, per-job budget | ✅ Done | Per-task/daily budgets, budget enforcement |
| FakeModel: canned responses for all profiles | ✅ Done | TestModelProvider for zero-cost testing |
| ToolCallParser: parses tool calls from LLM responses | ✅ Done | Regex + Jackson parsing, Function record support |
| CostTracker: per-task/daily budgets, budget enforcement | ✅ Done | Micro-dollar precision, alert thresholds |

### Step 1.3: Clarifier (engine-core) — Week 6
| Task | Status | Notes |
|------|--------|-------|
| PLANNER prompt with checklist | ⏳ Pending | |
| Returns ≤8 questions as JSON | ⏳ Pending | |
| Client sends answers → follow-up round | ⏳ Pending | |
| Produces RequirementsDoc v1 → writes requirements.md | ⏳ Pending | |

### Step 1.4: Prompt Refiner (engine-core) — Week 7
| Task | Status | Notes |
|------|--------|-------|
| Spring AI prompt template with 10 fixed sections | ⏳ Pending | |
| Fixed stack/rules from config | ⏳ Pending | |
| Validation: all sections present, all req IDs referenced | ⏳ Pending | |

### Step 1.5: Planner (engine-core) — Week 8
| Task | Status | Notes |
|------|--------|-------|
| Input: requirements.md + refined prompt → TaskPlan | ⏳ Pending | |
| Task fields: id, title, files[], dependsOn[], requirementIds[], acceptance[] | ⏳ Pending | |
| Validation: no cycles, all req IDs covered, relative paths | ⏳ Pending | |
| Topological sort for task order | ⏳ Pending | |

### Step 1.6: Skeleton Templates (engine-templates) — Week 9
| Task | Status | Notes |
|------|--------|-------|
| Spring Initializr baseline | ⏳ Pending | |
| Hardening: pinned versions, Checkstyle, SpotBugs, JaCoCo, Dependency-Check | ⏳ Pending | |
| Package layout: controller, service, repository, domain, dto, mapper, config, security, exception | ⏳ Pending | |
| Extras: Global exception handler, Dockerfile, docker-compose.yml, GitHub Actions CI | ⏳ Pending | |
| Verify: skeleton passes mvn verify + all scanners = 0 high findings | ⏳ Pending | |

### Step 1.7: Coder (engine-core) — Weeks 10-12
| Task | Status | Notes |
|------|--------|-------|
| Build prompt: task + plan/req excerpts + related files + conventions + security rules | ⏳ Pending | |
| Parse LLM tool calls with strict validation | ⏳ Pending | |
| File tools: path resolution, symlink check, size limits, .git/template protection | ⏳ Pending | |
| Staging area → pre-check → promote → compile in sandbox | ⏳ Pending | |
| Compile in sandbox → retry 3x on fail → mark for round 2 | ⏳ Pending | |

### Step 1.8: Sandbox (engine-sandbox) — Week 13
| Task | Status | Notes |
|------|--------|-------|
| Docker image: JDK + Maven + Scanners | ⏳ Pending | |
| Limits: memory/CPU/process caps, time limit, non-root | ⏳ Pending | |
| Maven cache volume (persistent) | ⏳ Pending | |
| Java wrapper: run(command, workspace, timeout) | ⏳ Pending | |

### Step 1.9: Verifier (engine-verify) — Weeks 14-15
| Tool | Status | Notes |
|------|--------|-------|
| Compile | ⏳ Pending | |
| Tests | ⏳ Pending | |
| Coverage | ⏳ Pending | |
| SpotBugs+FindSecBugs | ⏳ Pending | |
| Checkstyle | ⏳ Pending | |
| Semgrep | ⏳ Pending | |
| OWASP DepCheck + Trivy | ⏳ Pending | |
| Secrets | ⏳ Pending | |

### Step 1.10: Five-Round Fix Loop — Weeks 16-18
| Round | Status | Notes |
|-------|--------|-------|
| 1 (BUILD) | ⏳ Pending | |
| 2-5 (FIX) | ⏳ Pending | |
| Fingerprint diff | ⏳ Pending | |
| Regression guard | ⏳ Pending | |

### Step 1.11: Artifact Store (engine-store) — Weeks 19-20
| Component | Status | Notes |
|-----------|--------|-------|
| Folder: `<project>/.companion/` | ⏳ Pending | |
| Formats: 5 .md files + runs/ | ⏳ Pending | |
| DB: Jobs, Rounds, Findings, Fixes, Costs | ⏳ Pending | |

### Step 1.12: Evaluation Suite (eval/) — Weeks 21-22
| Task | Status | Notes |
|------|--------|-------|
| 30 Golden Specs | ⏳ Pending | |
| Runner: CLI main → each spec → fixed answers → CSV | ⏳ Pending | |

### Step 1.13: Hardening (v0.1) — Weeks 23-24
| Hardening | Status | Notes |
|-----------|--------|-------|
| Prompt injection defense | ⏳ Pending | |
| Secret scanning in prompts | ⏳ Pending | |
| Logging (job-id correlation, no secrets) | ⏳ Pending | |
| 70%+ unit test coverage | ⏳ Pending | |

---

## Phase 2: CLI Application (Weeks 39-50)

### Step 2.1: CLI App (app-cli) — Weeks 39-42 ✅ **COMPLETE**
| Task | Status | Notes |
|------|--------|-------|
| Create app-cli (picocli + Spring Boot) | ✅ Done | Spring Boot 3.4, picocli 4.7, WebFlux, shade plugin with relocation |
| Commands: generate, verify, doctor, project, task, config | ✅ Done | 6 commands + subcommands implemented |
| Fat JAR with dependency relocation | ✅ Done | maven-shade-plugin with Jackson, Spring, Reactor, Netty relocation |
| All 78+ tests pass | ✅ Done | Phase 1 tests + CLI compilation verified |

### Step 2.2: Config System — Weeks 42-43 ✅ **COMPLETE**
| Task | Status | Notes |
|------|--------|-------|
| CliConfig.java (record with serverUrl, outputFormat, timeout, apiKey, etc.) | ✅ Done | Record with defaults, merge, validation |
| ConfigManager.java (load/save/validate YAML at ~/.companion/config.yaml) | ✅ Done | SnakeYAML load/save with validation |
| ConfigCommand.java (get/set/list/path fully implemented) | ✅ Done | table/json/markdown/plain output formats |

### Step 2.3: REST Client & Error Handling — Week 2 ✅ **COMPLETE**
| Task | Status | Notes |
|------|--------|-------|
| OrchestratorClient.java (WebClient, retry, timeout, error mapping) | ✅ Done | Spring WebFlux WebClient with 3x retry, exponential backoff, error mapping |
| DTOs for all REST endpoints | ✅ Done | ProjectRequest/Response, CreateTaskRequest, TaskResponse, ApprovalRequest/Response, ArtifactsResponse, LogsResponse, HealthResponse |
| Error mapping: 400→BadRequest, 401→Unauthorized, 403→Forbidden, 404→NotFound, 409→Conflict, 500→ServerError, timeout→TimeoutException | ✅ Done | Custom exception hierarchy in OrchestratorClient |

### Step 2.4: Core Commands — Generate & Verify — Week 2-3 🔄 **IN PROGRESS**
| Sub-task | Status | Notes |
|----------|--------|-------|
| GenerateCommand (interactive flow with ClarifierPrompter, PlanReviewer, ProgressRenderer) | 🔄 **IN PROGRESS** | Skeleton with ClarifierPrompter, PlanReviewer, ProgressRenderer integration; needs Clarifier Q&A flow |
| VerifyCommand (verify existing project) | ✅ **DONE** | Full implementation with polling, health check, result display |

### Step 2.5: Project & Task Management Commands — Week 3 ✅ **COMPLETE**
| Sub-task | Status | Notes |
|----------|--------|-------|
| ProjectCommand (list, register) | ✅ Done | Implemented |
| TaskCommand (get, list, approve, reject, logs, artifacts) | ✅ Done | Implemented |

### Step 2.6: Doctor Command — Week 3 ✅ **COMPLETE**
| Sub-task | Status | Notes |
|----------|--------|-------|
| DoctorCommand (prerequisite checks) | ✅ Done | Full implementation: Java, Maven, Docker, Git, Server, Workspace, Disk, Port, JVM Heap checks with color-coded table output and remediation hints |

### Step 2.7: Interactive Terminal UX Polish — Weeks 43-44 ✅ **COMPLETE**
| Feature | Status | Notes |
|---------|--------|-------|
| Live progress line per stage | ✅ Done | ProgressRenderer with spinner + task table |
| Questions with suggested defaults | ✅ Done | ClarifierPrompter with defaults, help, multi-select |
| Colors, spinners, table formatting | ✅ Done | Ansi, Spinner, TablePrinter, OutputFormatter |

### Step 2.8: Artifact Viewing & Download — Week 4 ✅ **COMPLETE**
| Feature | Status | Notes |
|---------|--------|-------|
| task artifacts <id> --download --output | ✅ Done | Downloads all artifacts to specified directory |
| task artifacts <id> --view <file> | ✅ Done | View single artifact content in terminal |
| task artifacts <id> --download --output <dir> | ✅ Done | Download all artifacts to specified directory |
| task artifacts <id> --open | ⏳ Placeholder | Open in editor (not yet implemented) |

### Step 2.9: Packaging, Release & Distribution — Week 4-5 ✅ **COMPLETE**
| Task | Status | Notes |
|------|--------|-------|
| Executable JAR + launchers | ✅ Done | Fat JAR with shade plugin (dependency relocation + signature filtering) |
| GitHub Actions release workflow | ✅ Done | Build on tag, upload artifact, create Release with install script |
| Install script | ✅ Done | `curl -sSL .../install.sh | bash` downloads latest release |
| README.md | ✅ Done | Install, prerequisites, quick start, config, commands reference |

---

## Current Sprint Focus
**Week 39-42: Phase 2 Step 2.1 - CLI App (app-cli) ✅ COMPLETE**
- Created app-cli module with Spring Boot 3.4 + picocli 4.7 + WebFlux
- 6 commands implemented: generate, verify, doctor, project, task, config
- Fat JAR with maven-shade-plugin (dependency relocation for Jackson, Spring, Reactor, Netty)
- All 78+ tests pass (Phase 1 + CLI compilation)

**Week 42-43: Phase 2 Step 2.2 - Config System 🔄 IN PROGRESS**
- CliConfig.java: Record with serverUrl, defaultProject, outputFormat, timeoutSeconds, apiKey, logLevel, noColor
- ConfigManager.java: Load/save/validate YAML at ~/.companion/config.yaml (SnakeYAML)
- ConfigCommand.java: Fully implement get/set/list/path subcommands

---

## Known Issues / Blockers
- Test dependencies missing in engine-llm test scope (spring-boot-starter-test)
- Existing JPA entities in persistence/entity need to be separated from pure model records
- Existing ModelRouter, CostTracker, etc. in model/router need to be moved to engine-llm module

---

### Step 2.10: Integration Testing & Polish — Week 5-6 🔄 **PARTIALLY COMPLETE**
| Test | Status | Notes |
|------|--------|-------|
| E2E Happy Path | ⏳ Requires running server | 31 integration tests written, compile successfully |
| E2E Non-Interactive | ⏳ Requires running server | |
| E2E Verify Existing | ⏳ Requires running server | |
| E2E Doctor | ✅ Done | Verified working |
| Error Handling | ⏳ Requires running server | |
| Config Persistence | ✅ Done | Verified working |
| Cross-platform | ⏳ Pending | |