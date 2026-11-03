# Product Specification — Java Companion Orchestrator
## v1 CLI MVP — Implementation-Ready Spec

---

## 1. Product Objectives & Target Customers

### 1.1 Product Vision
> **A CLI tool (`companion.jar`) that generates a production-ready Spring Boot REST API from a natural language description, with built-in security scanning, testing, and auto-fixing — delivering a verified, audit-ready project in under 10 minutes.**

### 1.2 Target Customer (ICP)
| Attribute | Detail |
|-----------|--------|
| **Primary** | Java Tech Lead / Engineering Manager at regulated mid-size company (8-30 engineers) |
| **Industry** | Fintech, insurtech, healthtech, govtech |
| **Stack** | Spring Boot 3.x, Maven, PostgreSQL, Docker, GitHub/GitLab CI |
| **AI Usage** | Daily GitHub Copilot / Cursor users |
| **Pain** | AI PRs arrive 5-10x faster than review capacity; 40% have vulnerabilities; no audit trail |
| **Budget** | Team tool budget $500-2K/mo; influences org tooling |
| **Trigger** | Failed audit, security incident from AI code, new compliance requirement |

### 1.3 Narrow Launch Segment
Fintech/insurtech tech leads (10-50 engineers) with recent AI-related security incident or failed audit.

### 1.4 Success Metrics (Gate 2)
- Stranger downloads JAR → completes project in <10 min
- Project compiles, passes tests, 0 HIGH/CRITICAL findings
- All 5 artifact files generated correctly
- Cost tracked accurately (within 10% of actual)

---

## 2. MVP Features & Priorities

### 2.1 Essential Features (v1 — Must Have)

| Priority | Feature | Description | Acceptance |
|----------|---------|-------------|------------|
| **P0** | `companion generate` | Core value prop: natural language → verified Spring Boot project | Gate 2 |
| **P0** | Clarification Wizard | ≤8 questions with defaults; interactive + `--answers-file` | Gate 1 |
| **P0** | Plan Review + Approval | Traceability table (req → tasks); human-in-the-loop gate | Gate 1 |
| **P0** | 7-Scanner Verification | Compile, Test, Coverage, SpotBugs, Checkstyle, Semgrep, DepCheck+Trivy, Secrets | Gate 1 |
| **P0** | 5-Round Fix Loop | Round 1 build → Rounds 2-5 fix → fingerprint diff → regression guard | Gate 1 |
| **P0** | Fingerprint Tracking | SHA256(tool\|ruleId\|filePath\|codeLine) survives line changes | Gate 1 |
| **P0** | Regression Guard | New > fixed OR passing test fails → revert + retry once | Gate 1 |
| **P0** | Artifact Store | `.companion/` folder: 5 .md files + `runs/round-N.json` + SQLite DB | Gate 1 |
| **P0** | Cost Tracking + Budget | Per-job ceiling; hard stop at limit; NVIDIA free tier + Ollama fallback | Gate 1 |
| **P0** | `companion verify` | Run scanners + tests on existing Spring Boot project | Gate 2 |
| **P0** | `companion fix` | Run fix rounds on existing project | Gate 2 |
| **P0** | `companion doctor` | Diagnose prerequisites (Java, Maven, Docker, API keys) | Gate 2 |

### 2.2 Deferred (v1.1+)
- MCP Server, Web Dashboard, IntelliJ Plugin, Multi-language, Team workspaces, Gradle, GraphQL, JWT runtime auth

### 2.3 Single Project Type (v1 Scope)
**Only:** REST CRUD + JPA + Validation + Tests (Spring Boot, Maven, PostgreSQL/H2, JWT auth, JUnit 5 + Testcontainers)

---

## 3. Functional Requirements

### 3.1 FR-1: Clarification Flow
- **FR-1.1** Accept natural language via CLI argument or stdin
- **FR-1.2** Generate ≤8 clarifying questions as JSON (id, text, default, type: select/input/boolean)
- **FR-1.3** Accept answers interactively or via `--answers-file` (JSON)
- **FR-1.4** Produce `requirements.md` v1 with: original request, Q&A, assumptions, refined prompt
- **FR-1.4** Support follow-up round if required fields missing

### 3.2 FR-2: Prompt Refinement
- **FR-2.1** Template with 10 fixed sections: Goal, Fixed Stack, Entities, Endpoints, Security Rules, Validation Rules, Testing Rules, Conventions, Out of Scope, Acceptance Criteria
- **FR-2.2** Fixed stack/rules from config (not model): Java 21, Spring Boot 3.4, Maven, PostgreSQL, SEC-1 to SEC-14
- **FR-2.3** Validation: all sections present, all requirement IDs referenced
- **FR-2.4** Save to `requirements.md` with version history

### 3.3 FR-3: Task Planning
- **FR-3.1** Input: requirements.md + refined prompt → ordered `TaskPlan`
- **FR-3.2** Task fields: id (T-XXX), title, description, files[], dependsOn[], requirementIds[], acceptance[]
- **FR-3.3** Validation: no cycles (DAG), all req IDs covered, relative paths only, topological sort
- **FR-3.4** Typical order: Entities → Repositories → Services → Controllers → Security → Tests → Docker → README
- **FR-3.4** Output `plan.md` with traceability table (Requirement → Tasks)

### 3.4 FR-4: Approval Gate
- **FR-4.1** Display plan summary + traceability table
- **FR-4.2** Prompt for approval (y/N) with `--yes` flag for automation
- **FR-4.3** On reject: exit gracefully; on approve: persist approval → proceed to execution
- **FR-4.4** `companion approve <job-id> [--reject] [--note]` for async approval

### 3.5 FR-5: Code Generation (Coder)
- **FR-5.1** Execute tasks in topological order
- **FR-5.2** Per task: build prompt (task + plan/req excerpts + related files + conventions + security rules) → LLM (CODER profile) → parse tool calls
- **FR-5.3** Tool calls: writeFile, editFile, readFile, listDirectory, searchFiles (strict schema validation)
- **FR-5.4** File tools: path resolution (reject outside workspace), symlink check, size limits (10MB/file, 1000 files), .git/template protection
- **FR-5.5** Staging area → pre-check (secrets, dangerous patterns) → promote → compile in sandbox
- **FR-5.6** Compile in sandbox → retry 3x on fail → mark for round 2
- **FR-5.7** Git commit after each task (message: "T-XXX: <title>")

### 3.6 FR-6: Sandbox Execution
- **FR-6.1** Docker image: JDK 21 + Maven 3.9 + all 7 scanners
- **FR-6.2** Limits: `--memory=2g --cpus=2 --pids-limit=100 --user=1000:1000 --read-only --tmpfs /tmp --network=none`
- **FR-6.3** Maven cache volume (persistent across runs)
- **FR-6.4** API: `run(command, workspace, timeout)` → exitCode, stdout, stderr, durationMs
- **FR-6.5** Kill container on timeout/OOM; return structured error

### 3.7 FR-7: Verification Pipeline (7 Scanners)
| Scanner | Command | Output Parser | Severity Mapping |
|---------|---------|---------------|------------------|
| Compile | `mvn -q compile` | Compiler stderr | Errors → HIGH |
| Tests | `mvn verify` | Surefire XML | Failures → HIGH |
| Coverage | JaCoCo | jacoco.xml | <80% → MEDIUM |
| Static/Security | SpotBugs+FindSecBugs | XML | Per rule (SEC-3, SEC-5 → HIGH) |
| Code Style | Checkstyle | XML | Per rule (SEC-1, SEC-6 → HIGH) |
| Patterns | Semgrep (Java+Spring) | SARIF/JSON | Per rule (SEC-2, SEC-8, SEC-9 → HIGH) |
| Vuln Deps | OWASP DepCheck + Trivy | JSON | Per CVE (CVSS≥7 → HIGH) |
| Secrets | Semgrep secrets / gitleaks | JSON | Any → HIGH |

- **FR-7.1** Parallel execution of independent scanners
- **FR-7.2** Unified `Finding` model: fingerprint, tool, ruleId, severity, file, line, message, firstSeenRound, fixedInRound, status
- **FR-7.3** Fingerprint = SHA256(tool + "|" + ruleId + "|" + filePath + "|" + trimmedCodeLine)
- **FR-7.4** Output: `VerificationResult` { buildOk, testsPassed, testsFailed, coverage, findings[], costUsd, durationSec }

### 3.8 FR-8: Five-Round Fix Loop
- **Round 1 (BUILD):** Coder all tasks → Verifier → Record all findings as OPEN (firstSeenRound=1)
- **Rounds 2-5 (FIX):**
  1. Take OPEN findings by severity (CRITICAL→HIGH→MEDIUM→LOW), group by file
  2. Per group: Fixer LLM (findings + file content + related task) → edits via staging → promote → git commit
  3. Full Verifier re-run
  4. Fingerprint diff: Gone→FIXED, Still→OPEN, New→NEW
  5. Regression guard: If new > fixed OR passing test now fails → revert + retry once with failure info
  6. Write `fixes.md` + `vulnerabilities.md` per round
- **Stop Early:** No HIGH/CRITICAL + tests pass, OR no progress (0 fixed in round), OR budget spent
- **Rules:** Minimum necessary change, keep existing tests, never delete tests to pass

### 3.9 FR-9: Artifact Store
- **Folder:** `<project>/.companion/`
- **Files:** `requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md`, `runs/round-N.json`
- **DB (SQLite):** Jobs, Rounds, Findings, Fixes, Costs, Approvals, Project, ProjectMemory
- **Migrations:** Flyway V1 (core), V2 (cost tracking), V3 (approvals), V4 (workspaces - v2+)
- **Formats:** Frozen at v1.0; changes = version bump + migration note

### 3.10 FR-10: CLI Commands
| Command | Description |
|---------|-------------|
| `companion generate "spec" [options]` | Generate new project |
| `companion verify <folder> [options]` | Verify existing project |
| `companion fix <folder> [options]` | Fix existing project |
| `companion status <job-id>` | Show job state |
| `companion approve <job-id> [--reject]` | Approve/reject plan |
| `companion config <get|set|list>` | Manage config |
| `companion doctor [--fix]` | Diagnose prerequisites |

### 3.11 FR-11: Security Rules (Enforced by Verifier)
| ID | Rule | Enforcement |
|----|------|-------------|
| SEC-1 | Constructor injection only | ArchUnit |
| SEC-2 | No SQL concatenation | Semgrep |
| SEC-3 | BCrypt/Argon2 for passwords | SpotBugs |
| SEC-4 | SecureRandom for tokens | Semgrep |
| SEC-5 | No MD5/SHA1/DES/RC4/ECB | SpotBugs/Semgrep |
| SEC-6 | @Valid on @RequestBody | ArchUnit |
| SEC-7 | No secrets in code/logs | SecretScan |
| SEC-8 | CORS allow-list only | Semgrep |
| SEC-9 | Rate limiting on auth | Semgrep |
| SEC-10 | Actuator health only public | ArchUnit |

### 3.12 FR-12: Cost Tracking & Budget Enforcement
- **FR-12.1** Count tokens per LLM call (prompt + completion) → cost via price table (NVIDIA: free tier; OpenRouter: per-model pricing)
- **FR-12.2** Per-job budget (default $0.50, configurable via `--budget` or config)
- **FR-12.3** Daily budget (default $10.00)
- **FR-12.4** Hard stop: budget exceeded → job FAILED (BUDGET_EXCEEDED)
- **FR-12.5** Persistence: SQLite (Job, Round, TaskCost, DailyCost, CostAggregate)
- **FR-12.6** Alert at 80% of daily budget

---

## 4. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| **Performance** | End-to-end generation + verification | <10 minutes (typical CRUD) |
| **Performance** | ModelRouter failover | <500ms |
| **Performance** | Verifier (7 scanners parallel) | <60 seconds on skeleton |
| **Performance** | Sandbox startup | <5 seconds |
| **Reliability** | Resume from last saved state after crash | Job state persisted each round |
| **Reliability** | Fix loop termination | Max 5 rounds; early stop conditions |
| **Security** | Sandbox isolation | Docker non-root, read-only, no host mounts, no secret env vars |
| **Security** | Prompt injection defense | Strip instruction-like text from project files before LLM |
| **Security** | Secret scanning in prompts | Detect API keys, passwords, tokens before sending to LLM |
| **Security** | No secrets in logs | Structured logging with redaction; job-id correlation ID |
| **Cost Control** | Per-job budget ceiling | Hard stop at configured limit |
| **Cost Control** | Daily budget ceiling | Hard stop at configured limit |
| **Portability** | Runs on Linux/macOS/Windows | Via Docker; JAR executable |
| **Offline Mode** | `--offline` flag | Uses local Ollama model (codellama:13b) |
| **Observability** | Logging | job-id correlation in every log line; structured JSON in prod |
| **Observability** | Metrics | Micrometer + Actuator: jobs, tokens, cost, findings, sandbox duration |
| **Observability** | Health | `/actuator/health` shows provider status, circuit breaker, cost |

---

## 5. User Stories & Workflows

### 5.1 Primary: Generate New Project
```gherkin
As a Java Tech Lead
I want to run `companion generate "REST CRUD for Book with JWT auth" --name library`
So that I get a verified Spring Boot project with audit trail in <10 minutes

Workflow:
1. CLI asks 5 clarifying questions (DB, Auth, API Style, Tests, Packaging) with defaults
2. I answer (Enter = accept default)
3. requirements.md generated → I review
4. plan.md generated with 12 tasks + traceability table → I approve (y/N)
4. Engine generates: Entity, Repository, Service, Controller, DTOs, Tests, Security, Dockerfile
5. Round 1: Build + Verify → findings displayed
6. Rounds 2-5: Auto-fix → re-verify → progress bar
7. Final: Project at ./library with .companion/{requirements.md,plan.md,fixes.md,vulnerabilities.md,report.md}
8. `mvn verify` passes; 0 HIGH/CRITICAL findings
```

### 5.2 Secondary: Verify Existing Project
```gherkin
As a Java Tech Lead
I want to run `companion verify ./my-spring-project --scanners semgrep,spotbugs,depcheck`
So that I get a security audit report without generating new code

Workflow:
1. Runs all/selected scanners on existing project
2. Displays findings by severity
3. Exits non-zero if `--fail-on HIGH` and findings exist
4. Outputs SARIF/JSON for CI integration
```

### 5.3 Secondary: Fix Existing Project
```gherkin
As a Java Tech Lead
I want to run `companion fix ./my-project --max-rounds 3 --only security`
So that security findings are auto-fixed with verification

Workflow:
1. Runs verifier → groups findings by file
2. Fix rounds (max 3) → edits via staging → git commit
3. Re-verifies each round
4. Outputs fixes.md + updated vulnerabilities.md
```

### 5.4 Onboarding: Doctor
```gherkin
As a new user
I want to run `companion doctor`
So that I know exactly what's missing before I try to generate

Output:
✅ Java 21.0.5 (Temurin)
✅ Maven 3.9.6
✅ Docker 27.1.1 (running)
✅ Git 2.45.1
❌ NVIDIA_API_KEY not set (required for generation)
✅ ~/.companion/config.yml exists
✅ SQLite 3.46.0
⚠️  Semgrep 1.100.0 (latest: 1.101.0)
```

---

## 6. Authentication & Authorization

### 6.1 v1 Model: API Key Only
- **No user accounts, no SSO, no RBAC** — single-user CLI tool
- **NVIDIA API Key:** Required for generation; stored in `~/.companion/config.yml` or `NVIDIA_API_KEY` env var
- **OpenRouter/Ollama:** Optional fallbacks; configured via `companion config set provider`
- **License Key (v1.1+):** Offline JWT validation for paid features; not in v1

### 6.2 Project-Level Access
- **No multi-tenancy in v1** — each project isolated in its own directory
- **Project registration:** `companion project register <path>` stores metadata in SQLite
- **No authentication between projects** — local file system boundaries

---

## 7. Database & API Requirements

### 7.1 Database (SQLite Production / H2 Test)

#### Core Tables (Flyway V1)
```sql
-- Jobs
CREATE TABLE jobs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL,  -- CREATED, CLARIFYING, REQUIREMENTS_READY, PROMPT_REFINED, PLANNED, AWAITING_APPROVAL, BUILDING, VERIFYING, FIXING, COMPLETED, FAILED, CANCELLED
    round INT DEFAULT 1,
    budget_usd DECIMAL(10,6),
    cost_usd DECIMAL(10,6) DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    error TEXT
);

-- Rounds
CREATE TABLE rounds (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    round_num INT NOT NULL,
    build_ok BOOLEAN,
    tests_passed INT,
    tests_failed INT,
    coverage DECIMAL(5,2),
    cost_usd DECIMAL(10,6),
    duration_sec INT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Findings
CREATE TABLE findings (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    tool VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128),
    severity VARCHAR(16) NOT NULL,  -- INFO, LOW, MEDIUM, HIGH, CRITICAL
    file VARCHAR(512),
    line INT,
    message TEXT,
    first_seen_round INT NOT NULL,
    fixed_in_round INT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN, FIXED, REGRESSED, ACCEPTED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Fixes
CREATE TABLE fixes (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    finding_fingerprint VARCHAR(64) NOT NULL,
    round INT NOT NULL,
    summary TEXT,
    files_changed TEXT,  -- JSON array
    result VARCHAR(16) NOT NULL,  -- VERIFIED_FIXED, REVERTED, ACCEPTED, FAILED
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Approvals
CREATE TABLE approvals (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,  -- PENDING, APPROVED, REJECTED, TIMEOUT
    approver VARCHAR(128),
    comment VARCHAR(4000),
    requested_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE
);

-- Cost Tracking (V2)
CREATE TABLE task_costs (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    provider VARCHAR(64),
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    start_time TIMESTAMP NOT NULL,
    last_update TIMESTAMP NOT NULL
);

CREATE TABLE daily_costs (
    date DATE PRIMARY KEY,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    cost_usd_micro BIGINT DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### Indexes
- `idx_jobs_project`, `idx_jobs_state`, `idx_jobs_created`
- `idx_rounds_job`, `idx_rounds_num`
- `idx_findings_job`, `idx_findings_fingerprint`, `idx_findings_severity`, `idx_findings_status`
- `idx_fixes_job`, `idx_fixes_fingerprint`

### 7.2 Internal Engine Interfaces (No HTTP API in v1)

#### ModelRouter
```java
interface ModelRouter {
    Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile);
    Mono<ChatResponse> chat(ChatRequest request);  // default profile
    List<String> getAvailableProviders();
    Map<String, CircuitBreaker.State> getCircuitBreakerStates();
    CostTracker getCostTracker();
}
```

#### ToolRegistry
```java
interface ToolRegistry {
    ToolResult execute(Project project, String toolName, String jsonArgs, PermissionContext ctx);
    Map<String, ToolSpec> getAllSpecs();
}
```

#### Tool Specs (Key Tools)
| Tool | Input Schema | Output |
|------|--------------|--------|
| `writeFile` | `{path, content}` | `{path, size, backupPath}` |
| `editFile` | `{path, oldText, newText}` | `{path, replacements, backupPath}` |
| `readFile` | `{path}` | `{path, content}` |
| `listDirectory` | `{path, maxDepth}` | `{files[], dirs[]}` |
| `searchFiles` | `{pattern, contentPattern, path, maxResults}` | `{matches[]}` |
| `runMavenBuild` | `{path, goals[], timeout, skipTests}` | `{exitCode, output, success}` |
| `runMavenTest` | `{path, goals[], timeout}` | `{exitCode, output, success}` |
| `semgrepScan` | `{path, config, timeout}` | `{exitCode, output, findings[]}` |
| `trivyScan` | `{path, target, timeout}` | `{exitCode, output, vulnerabilities[]}` |
| `dependencyCheck` | `{path, timeout}` | `{exitCode, output, vulnerabilities[]}` |
| `secretScan` | `{path, timeout}` | `{exitCode, output, findings[]}` |

---

## 8. Payment & Subscription Requirements

### 8.1 v1: No Payment Integration
- **CLI is free to download and use** during v1 validation period
- **License enforcement deferred to v1.1+**
- **Revenue validation via paid pilots** ($2,500 for 5-day audit/fix service)

### 8.2 v1.1+ Planned Model
| Tier | Price | Model |
|------|-------|-------|
| Professional Annual | $399/seat/yr | Subscription |
| Professional Lifetime | $1,199/seat | One-time (3 years updates) |
| Team Pack (5 seats) | $1,599/yr | Subscription |
| Enterprise (10+) | Custom | Contract |

### 8.3 License Enforcement (v1.1+)
- Offline JWT validation (no license server required)
- JReleaser signed artifacts
- Grace period: 30 days offline

---

## 9. Security & Privacy Requirements

### 9.1 Sandbox Isolation
- Docker: `--user=1000:1000 --read-only --tmpfs /tmp --network=none --memory=2g --cpus=2 --pids-limit=100`
- No host mounts except workspace (`-v workspace:/workspace:rw`)
- Maven cache volume (named, persistent)
- No secret environment variables passed to container

### 9.2 Prompt Safety
- Strip instruction-like text from project files before sending to LLM
- Scan prompts for: API keys, passwords, tokens, connection strings → redact before LLM call
- Never log prompts/responses at DEBUG level in production

### 9.3 Data Handling
- **No telemetry by default** — opt-in only
- **Local-first:** All code, artifacts, DB stay on user machine
- **NVIDIA API:** Only sends prompts; no code upload unless explicitly configured
- **Ollama fallback:** Fully local, zero network egress

### 9.4 Artifact Security
- `fixes.md` / `vulnerabilities.md` may contain code snippets → user controls sharing
- SQLite DB: Local file, user owns permissions
- No cloud sync in v1

### 9.5 Compliance
- **Export control:** EAR99 (development tools)
- **Encryption:** None in v1 (local files only)
- **Data retention:** User controls; `companion project unregister` deletes DB records

---

## 10. Edge Cases & Failure Scenarios

| Scenario | Detection | Handling | User Experience |
|----------|-----------|----------|-----------------|
| **Java/Maven/Docker not installed** | `companion doctor` pre-check | Exit with `JAVA_NOT_FOUND`/`MAVEN_NOT_FOUND`/`DOCKER_NOT_RUNNING` + recovery action | Clear error + install command |
| **Docker permission denied** | Sandbox startup fails | Exit with `DOCKER_PERMISSION_DENIED` + `usermod` instruction | Actionable error |
| **NVIDIA API key missing** | ModelRouter initialization | Exit with `NVIDIA_API_KEY_MISSING` + config instruction | `companion config set nvidia.api-key` |
| **NVIDIA rate limit (429)** | HTTP 429 from provider | Retry with exponential backoff (max 3); then failover to OpenRouter/Ollama | Transparent retry |
| **NVIDIA invalid key (401)** | HTTP 401 | Exit with `NVIDIA_API_KEY_INVALID` + regeneration URL | Actionable error |
| **LLM timeout (60s)** | ModelRouter timeout | Retry (max 3); then failover | Transparent retry |
| **LLM malformed tool calls** | ToolCallParser validation failure | Retry same task (max 3); then mark task FAILED | Next task continues |
| **Compile failure in sandbox** | Exit code ≠ 0 | Retry 3x with LLM feedback; then mark for round 2 | Progress continues |
| **Test failure** | Surefire XML parse | Record as HIGH finding; fix loop addresses | Auto-fix in rounds 2-5 |
| **Scanner crash/timeout** | Tool timeout/exception | Record as SCANNER_FAILED finding; continue other scanners | Partial results |
| **Budget exceeded** | CostTracker check before each LLM call | Job FAILED (BUDGET_EXCEEDED); artifacts preserved | Clear message + `--budget` hint |
| **Fix loop regression** | New findings > fixed findings OR passing test fails | Revert last round's commits; retry once with failure context | Automatic recovery |
| **Fix loop no progress** | 0 findings fixed in round | Stop early; report remaining findings | User decides next step |
| **Sandbox OOM** | Container killed (exit 137) | Retry with `--memory 4g` hint | Actionable error |
| **Sandbox timeout** | Duration > limit | Kill container; record SANDBOX_TIMEOUT | `--timeout` hint |
| **Prompt injection attempt** | Detected in project files | Strip/block; log security event | Silent protection |
| **Secret in prompt** | Regex scan before LLM call | Redact; log security event | Silent protection |
| **Disk full** | IOException on write | Graceful shutdown; preserve partial artifacts | Clear error |
| **Corrupt SQLite** | Connection/SQL error | Backup `.bak`; attempt recovery; else reinit | Data loss warning |
| **Spring Boot version drift** | Template validation fails | Pinned versions; quarterly upgrade test | Documented process |

---

## 11. Acceptance Criteria (Per Feature)

### 11.1 Clarification Wizard
- [ ] `companion generate "REST CRUD for Book"` → asks exactly 5 questions
- [ ] Each question has: id, text, default, type (select/input/boolean)
- [ ] Enter key accepts default; custom input validated
- [ ] `--answers-file` JSON works non-interactively
- [ ] Output `requirements.md` with all sections populated

### 11.2 Plan Generation
- [ ] Plan has 10-15 tasks for typical CRUD spec
- [ ] Tasks have: id (T-XXX), title, files[], dependsOn[], requirementIds[], acceptance[]
- [ ] No circular dependencies (DAG validation passes)
- [ ] All requirement IDs from requirements.md covered
- [ ] Topological order: Entities → Repos → Services → Controllers → Security → Tests → Docker → README
- [ ] `plan.md` includes traceability table (Requirement → Task IDs)

### 11.3 Approval Gate
- [ ] Interactive prompt: `Approve plan? [Y/n]`
- [ ] `--yes` flag skips prompt
- [ ] `companion approve <job-id>` works async
- [ ] Rejection exits with code 0, no artifacts generated

### 11.4 Code Generation
- [ ] Generates: Entity, Repository, Service, Controller, DTOs, Mapper, SecurityConfig, Testcontainers tests, Dockerfile, README
- [ ] All files compile in sandbox (Round 1)
- [ ] Follows conventions: constructor injection, records for DTOs, MapStruct, Jakarta validation
- [ ] No files outside workspace; no symlinks; size limits enforced

### 11.5 Verification Pipeline
- [ ] All 7 scanners execute in parallel
- [ ] Compile errors → HIGH findings
- [ ] Test failures → HIGH findings
- [ ] Coverage <80% → MEDIUM finding
- [ ] SpotBugs SEC-3/SEC-5 → HIGH
- [ ] Checkstyle SEC-1/SEC-6 → HIGH
- [ ] Semgrep SEC-2/SEC-8/SEC-9 → HIGH
- [ ] DepCheck/Trivy CVSS≥7 → HIGH
- [ ] Any secret → HIGH
- [ ] Output `VerificationResult` with all findings

### 11.5 Fix Loop
- [ ] Round 1: All findings recorded OPEN (firstSeenRound=1)
- [ ] Rounds 2-5: OPEN grouped by file, CRITICAL first
- [ ] Fixer produces edits → staging → promote → git commit
- [ ] Full re-verify each round
- [ ] Fingerprint diff: Gone=FIXED, Still=OPEN, New=NEW
- [ ] Regression guard: new > fixed → revert + retry once
- [ ] Stop early: no HIGH/CRITICAL + tests pass
- [ ] `fixes.md` + `vulnerabilities.md` written each round

### 11.6 Artifact Store
- [ ] `.companion/` folder created with 5 .md files + `runs/`
- [ ] SQLite DB: jobs, rounds, findings, fixes, approvals, costs tables populated
- [ ] Formats match spec exactly (forward compatible)
- [ ] `companion verify` on existing project produces same artifacts

### 11.7 CLI Commands
- [ ] `generate` / `verify` / `fix` / `status` / `approve` / `config` / `doctor` all work
- [ ] Global flags: `--json`, `--verbose`, `--quiet`, `--yes`, `--max-rounds`, `--budget`, `--offline`
- [ ] `companion doctor` diagnoses all prerequisites correctly
- [ ] `--json` output matches schema exactly

### 11.8 Security Rules
- [ ] SEC-1: Constructor injection only (ArchUnit test passes)
- [ ] SEC-2: No SQL concatenation (Semgrep rule catches)
- [ ] SEC-3: BCrypt/Argon2 (SpotBugs rule catches)
- [ ] SEC-4: SecureRandom (Semgrep rule catches)
- [ ] SEC-5: No weak crypto (SpotBugs/Semgrep catches)
- [ ] SEC-6: @Valid on @RequestBody (ArchUnit test passes)
- [ ] SEC-7: No secrets (SecretScan catches)
- [ ] SEC-8: CORS allow-list (Semgrep catches)
- [ ] SEC-9: Rate limiting (Semgrep catches)
- [ ] SEC-10: Actuator health only (ArchUnit test passes)

### 11.9 Cost Tracking
- [ ] Tokens counted per LLM call
- [ ] Cost calculated via price table
- [ ] Per-job budget enforced (hard stop)
- [ ] Daily budget enforced (hard stop)
- [ ] Alert at 80% daily budget
- [ ] SQLite persistence survives restart

### 11.10 Stranger Test (Gate 2)
- [ ] Fresh machine: Java 21, Maven, Docker, Git installed
- [ ] Download `companion.jar` from GitHub Release
- [ ] Run `companion generate "REST CRUD for Book" --name library`
- [ ] Answer 5 questions, approve plan
- [ ] Project compiles, `mvn verify` passes, 0 HIGH/CRITICAL
- [ ] Total time < 10 minutes

---

## 12. Explicit Out-of-Scope Features

| Feature | Deferred To | Reason |
|---------|-------------|--------|
| MCP Server / Agent Plugin | v1.1+ | Separate distribution channel |
| Web Application (REST API + React UI) | v2+ | Different product; requires team |
| IntelliJ Plugin | v2+ | Distribution channel; separate skillset |
| Mobile App (React Native) | v2+ | Not core to CLI value prop |
| Multi-language (Python, Go, Node.js) | v2+ | 10x complexity; Java-only for v1 |
| Web Dashboard / Team Features / Billing | v2+ | Requires web app + multi-tenancy |
| Gradle Build Tool | v1.1+ | Maven only reduces template complexity |
| GraphQL API Generation | v2+ | REST only for v1 |
| JWT/OAuth Runtime Auth | v1.1+ | API key sufficient for CLI |
| Multi-project Workspaces | v2+ | Single-project focus for v1 |
| Advanced Scheduling | v2+ | Not needed for CLI |
| Custom Rule Packs | v1.1+ | SEC-1 to SEC-14 hardcoded for v1 |
| Self-Hosted LLM (beyond Ollama) | v1.1+ | Ollama covers offline need |
| PR/GitHub Integration | v1.1+ | CLI-only for v1 |

---

## 13. Measurable Launch Success Criteria

### 13.1 Gate 0 (Foundation - Week 2)
- [ ] Java 21, Maven 3.9+, Docker, Git installed & verified
- [ ] GitHub repo + CI (`mvn verify` on push)
- [ ] Spring Boot CRUD API + tests built independently
- [ ] First LLM call from Java works
- [ ] `docs/VERSIONS.md`, `DIARY.md`, `DECISIONS.md` created

### 13.2 Gate 1 (Core Engine - Week 36)
- [ ] Data model records compile + JSON round-trip tests pass
- [ ] ModelRouter: primary→fallback failover works; budget enforcement works
- [ ] FakeModel: canned responses for all profiles (tests offline, zero cost)
- [ ] Clarifier: "REST CRUD for Book" → sensible questions → requirements.md
- [ ] Refiner: produces structured prompt with all 10 sections
- [ ] Planner: valid plan, no cycles, all reqs covered, topological sort
- [ ] Skeleton: `mvn verify` + all scanners = 0 HIGH findings
- [ ] Coder: 3-entity CRUD compiles in sandbox
- [ ] Sandbox: malicious test contained + killed
- [ ] Verifier: planted SQLi, vuln dep, failing test detected exactly
- [ ] Fix loop: 5 planted issues fixed without breaking tests
- [ ] Artifact store: 5 .md files + runs/ + DB matches
- [ ] Eval runner: 10 specs → metrics table (90% compile, 85% tests, 0 HIGH/CRITICAL on 8/10)

### 13.3 Gate 2 (CLI Works - Week 48)
- [ ] `companion generate "..." --name x` works end-to-end
- [ ] `companion verify <folder>` works on existing projects
- [ ] `companion doctor` diagnoses missing prerequisites
- [ ] Stranger test: Fresh machine → download JAR → complete project in <10 min

### 13.4 Business Validation (Parallel, 12 Weeks)
- [ ] **E1 Usability:** 5 tech leads complete flow <15 min; 4/5 say "I'd use this"
- [ ] **E2 Real Projects:** 10 real Spring Boot repos → 8/10 reach 0 HIGH/CRITICAL
- [ ] **E3 Outreach:** 20 DMs → 3+ replies; 1+ demo booked
- [ ] **E4 Model:** NVIDIA ≥70% compile success vs GPT-4o on 10 specs
- [ ] **E5 Audit:** 2 auditors accept `fixes.md`/`vulnerabilities.md` format
- [ ] **E6 Docker:** 15/20 prospects can run Docker/Podman
- [ ] **E7 Pricing:** 6/10 prospects say "Yes" at $399/seat/yr
- [ ] **R1 Pilots:** 3 paid pilots at $2,500 each
- [ ] **R3 Conversions:** 2/3 pilots convert to $399/seat/yr annual
- [ ] **Total ARR:** ≥$10,000 before Phase 2 investment

---

## 14. Open Questions Requiring Decisions

| ID | Question | Impact | Recommendation | Decision Needed By |
|----|----------|--------|----------------|-------------------|
| **Q1** | CLI vs IDE plugin for daily use? | Distribution | Run E1 first; if CLI rejected → pivot to IntelliJ Plugin first | Week 2 (E1 result) |
| **Q2** | Fix loop on business logic (not just CRUD)? | TAM | Run E2; if fails → narrow to CRUD-only "scaffolding accelerator" | Gate 1 |
| **Q3** | Real cost per project at scale? | Pricing | Measure during E2; budget enforcement must work | Gate 1 |
| **Q4** | Docker Desktop allowed in target corps? | Adoption | Run E6; if <75% → build Podman/Colima fallback | Week 4 |
| **Q5** | Include `companion verify` in v1? | TAM expansion | **YES** — easiest entry point for existing codebases | Now |
| **Q6** | License enforcement offline? | v1.1 revenue | Defer to v1.1; JReleaser + signed JWT | Phase 2 |

---

## 15. Appendix: Key Technical Decisions (from DECISIONS.md)

| DEC | Decision | Rationale |
|-----|----------|-----------|
| DEC-001 | v1 Scope: CLI only | 6-12 month achievable for beginner |
| DEC-002 | Single project type (REST CRUD) | Reduces Planner/Coder complexity 10x |
| DEC-003 | Single LLM provider (NVIDIA) | Simplifies ModelRouter; free tier |
| DEC-004 | SQLite (prod) / H2 (test) | Zero-config, file-based, Flyway-compatible |
| DEC-005 | @Lazy on ProviderHealthController | Breaks ModelRouter ↔ ProviderHealthController cycle |
| DEC-006 | Test profile uses "test" provider | No API keys in CI; canned responses |
| DEC-007 | 5-round fix loop + early stop | Prevents infinite loops; budget protection |
| DEC-007 | FakeModel for all testing | Zero cost, zero latency, deterministic |
| DEC-008 | Sandbox = Docker per job | Isolation for untrusted generated code |
| DEC-009 | Fingerprint = hash(tool\|ruleId\|filePath\|codeLine) | Survives line number changes |
| DEC-010 | Security rules = MUST in Verifier | Enforced by ArchUnit/Checkstyle/Semgrep |
| DEC-011 | Cost tracking per job + budget | Hard stop at ceiling |
| DEC-012 | CLI = picocli + Spring Boot | Mature, subcommands, help, completion |
| DEC-013 | Artifact formats frozen at v1.0 | Stable for web/IDE/mobile consumers |

---

## 16. Implementation Phases Summary

| Phase | Weeks | Deliverable | Gate |
|-------|-------|-------------|------|
| **0** | 1-2 | Tools, Repo, CI, First LLM call, Spring Boot CRUD learning | Gate 0 |
| **1.1** | 3 | Data model records (engine-model) | — |
| **1.2** | 4-5 | LLM Layer (engine-llm): Router, Providers, CostTracker, FakeModel | — |
| **1.3** | 6 | Clarifier (engine-core) | — |
| **1.4** | 7 | Prompt Refiner (engine-core) | — |
| **1.5** | 8 | Planner (engine-core) | — |
| **1.6** | 9 | Skeleton Templates (engine-templates) | — |
| **1.7** | 10-12 | Coder (engine-core) | — |
| **1.8** | 13 | Sandbox (engine-sandbox) | — |
| **1.9** | 14-15 | Verifier (engine-verify) | — |
| **1.10** | 16-18 | Fix Loop (engine-core) | — |
| **1.11** | 19-20 | Artifact Store (engine-store) | — |
| **1.12** | 21-22 | Evaluation Suite (eval/) | — |
| **1.13** | 23-24 | Hardening (v0.1) | Gate 1 |
| **2.1** | 25-28 | CLI App (app-cli) | — |
| **2.2** | 29-30 | Terminal UX Polish | — |
| **2.3** | 31-32 | Release & Distribution | Gate 2 |

**Total v1: 36-52 weeks (beginner, 15-20 hrs/week)**

---

*This specification is the contract for v1 implementation. All code must conform to these requirements. Update only with explicit approval and version bump.*