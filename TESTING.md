# Orchestrator Testing Guide

## Current State (Phase 1 Complete, Phase 2 Not Started)

| Component | Status | Testable Without CLI? |
|-----------|--------|----------------------|
| **Engine Model** | ✅ Done | Unit tests only |
| **Engine LLM** | ✅ Done | Unit tests only |
| **Engine Sandbox** | ✅ Done | Unit tests only (13 tests pass) |
| **Engine Verify** | ✅ Done | Unit tests only (7 tests pass) |
| **Engine Store** | ✅ Done | Unit tests only (8 tests pass) |
| **Engine Core** | ✅ Done | Unit tests only (25 tests pass) |
| **Engine Templates** | ✅ Done | Unit tests only (8 tests pass) |
| **Engine Eval** | ✅ Done | Unit tests only (4 tests pass) |
| **App CLI** | ❌ **EMPTY** - No source files | **NO - This is the missing interface** |

---

## What You CAN Test Right Now (Without CLI)

### 1. Run All Unit/Integration Tests
```bash
cd D:\ANUP\WASTE\orchestrator\orchestrator
mvn clean test
```
**Result:** All 10 modules build, all tests pass (78+ tests total).

### 2. Test Individual Engine Modules
```bash
# Sandbox security features (path traversal, command injection, etc.)
cd engine-sandbox && mvn test

# Core orchestration (clarifier, planner, coder, refiner)
cd engine-core && mvn test

# Artifact storage & retrieval
cd engine-store && mvn test

# Template generation
cd engine-templates && mvn test

# Evaluation suite
cd engine-eval && mvn test
```

### 3. Test REST API Directly (If You Start the Server)
```bash
# Start the orchestrator application
cd orchestrator && mvn spring-boot:run

# Then use curl/Postman:
# POST /api/projects - Register a project
# POST /api/tasks - Submit a task
# GET /api/tasks/{id} - Get task status
# POST /api/approvals/{taskId}/approve - Approve task
# POST /api/approvals/{taskId}/reject - Reject task
# GET /api/projects - List projects
```

### 4. Test Sandbox Security Features (Unit Tests Cover This)
The `SandboxServiceTest` validates:
- ✅ Path traversal prevention (`../etc/passwd`, `..\windows\system32`)
- ✅ Command injection prevention (`;`, `&&`, `|`, backticks, `$()`)
- ✅ Workspace boundary enforcement
- ✅ Windows reserved names blocking
- ✅ Command length limits
- ✅ Quoted argument parsing
- ✅ Secret masking in logs

---

## What You CANNOT Test Without CLI (Real Developer Workflow)

### The Missing End-to-End Flow
```
Developer wants to build a "User Management API"
         │
         ▼
    [CLI] task submit "Build a User Management API with CRUD endpoints"
         │
         ▼
    [Clarifier] Asks 4-8 clarifying questions (interactive)
         │
         ▼
    [Planner] Generates 5-10 task plan
         │
         ▼
    [Approval] Developer reviews & approves plan
         │
         ▼
    [Coder] Executes tasks in sandbox (mvn compile, tests)
         │
         ▼
    [Verifier] Runs verification (compile, test, security scan)
         │
         ▼
    [Fix Loop] If failed → AI fixes → re-verify (max 3 rounds)
         │
         ▼
    [Artifacts] requirements.md, plan.md, fixes.md, report.md saved
```

### Without CLI, You Must:
1. **Manually** create JSON payloads for REST API
2. **Manually** poll task status
3. **Manually** call approval endpoints
4. **No interactive clarification** (Clarifier needs CLI for Q&A)
5. **No file artifact viewing** (artifacts saved to `.companion/` but no CLI to read them)

---

## What Phase 2 (CLI) Must Implement

### Required CLI Commands
| Command | Purpose | Engine Module |
|---------|---------|---------------|
| `project register <name> <path>` | Register project workspace | Store |
| `project list` | List registered projects | Store |
| `task submit <project> "<description>"` | Submit new task | Core/Store |
| `task get <taskId>` | View task status, logs, artifacts | Core/Store |
| `task list [--project <id>]` | List tasks | Core/Store |
| `task approve <taskId> [--comment "..."]` | Approve plan | Approval/Core |
| `task reject <taskId> --reason "..."` | Reject plan | Approval/Core |
| `task logs <taskId> [--follow]` | Stream task logs | Core |
| `task artifacts <taskId> [--download]` | View/download artifacts | Store |

### CLI Architecture Needed
```
app-cli/
├── src/main/java/com/example/orchestrator/cli/
│   ├── OrchestratorCli.java          # Picocli main entry
│   ├── ProjectCommands.java          # project register/list
│   ├── TaskCommands.java             # task submit/get/list/approve/reject/logs/artifacts
│   ├── OutputFormatter.java          # JSON/Table/Markdown output
│   ├── InteractivePrompter.java      # For Clarifier Q&A
│   └── Config.java                   # CLI config (server URL, auth)
```

### Integration Points
- **REST Client**: Calls `TaskController`, `ProjectController`, `ApprovalController`
- **WebSocket/SSE**: For real-time task logs (`task logs --follow`)
- **Config**: `~/.orchestrator/cli.yaml` (server URL, default project, auth token)

---

## Recommended Next Steps

### Option A: Build CLI First (Recommended)
```bash
# 1. Implement CLI commands in app-cli module
# 2. Build: mvn clean install -pl app-cli -am
# 3. Test: java -jar app-cli/target/app-cli.jar task submit my-project "Build a REST API"
# 4. Full end-to-end validation
```

### Option B: Quick API Smoke Test (If You Must Test Now)
```bash
# Terminal 1: Start server
cd orchestrator && mvn spring-boot:run

# Terminal 2: Test via curl
# 1. Register project
curl -X POST localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{"name":"test-proj","rootPath":"/home/user/projects/test-proj","description":"Test project"}'

# 2. Submit task
curl -X POST localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"projectId":"test-proj","request":"Create a simple REST controller","requester":"test-user"}'

# 3. Get task ID from response, then poll status
curl localhost:8080/api/tasks/TASK-XXXXXX

# 4. Approve when WAITING_APPROVAL
curl -X POST localhost:8080/api/approvals/TASK-XXXXXX/approve \
  -H "Content-Type: application/json" \
  -d '{"approver":"test-user","comment":"Looks good"}'

# 5. Monitor until COMPLETED/FAILED
```

---

## Known Limitations (Even With CLI)

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| **No WebSocket/SSE** | Can't stream logs in real-time | Poll `/api/tasks/{id}` or add SSE later |
| **No auth in CLI** | Anyone can submit tasks | Add JWT/API key auth |
| **Clarifier non-interactive** | Uses fallback questions | Need `InteractivePrompter` in CLI |
| **No parallel task execution** | Sequential only | Future: async task execution |
| **Sandbox needs Docker** | Won't work without Docker daemon | Document requirement |
| **No project templating CLI** | `project init` missing | Add template selection |

---

## Verification Checklist Before Release

- [ ] **CLI implemented** with all required commands
- [ ] **End-to-end test**: `task submit` → `task approve` → `task get` shows COMPLETED
- [ ] **Artifacts generated**: `.companion/<project>/<job>/requirements.md`, `plan.md`, `fixes.md`, `report.md`
- [ ] **Sandbox works**: `mvn clean verify` runs in container, produces JAR
- [ ] **Fix loop triggers**: On test failure, AI generates fixes, re-verifies
- [ ] **Security scans run**: SecretScan, DependencyCheck, Semgrep in verification
- [ ] **Cost tracking works**: Token usage recorded per task
- [ ] **State persistence**: Can resume after restart (StateManager)

---

## Summary

| Question | Answer |
|----------|--------|
| Can I test fully without CLI? | **No** - CLI is the developer interface |
| Are unit tests enough? | They verify components, not workflow |
| Should I build CLI first? | **Yes** - Phase 2 is the CLI |
| What's the fastest path? | Implement `TaskCommands` + `ProjectCommands` in `app-cli` |

**Bottom line:** Build the CLI (Phase 2). That's what makes this usable by "any coding agent" or human developer. The engine is ready; it just needs the steering wheel.