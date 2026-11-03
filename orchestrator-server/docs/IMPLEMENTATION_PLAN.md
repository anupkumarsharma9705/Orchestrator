# Implementation Plan
## Java Companion Orchestrator - v1 (CLI MVP)

> **Status**: ACTIVE | **Target**: 36-52 weeks (beginner, 15-20 hrs/week)
> **Gate 0 Target**: End of Week 2 | **Gate 1 Target**: Week 36-38

---

## 1. Phase Overview

| Phase | Duration | Focus | Gate |
|-------|----------|-------|------|
| **Phase 0** | Weeks 1-2 | Setup, Learning, Foundation | Gate 0 |
| **Phase 1** | Weeks 3-38 | Core Engine (13 steps) | Gate 1 |
| **Phase 2** | Weeks 39-50 | CLI Application | Gate 2 |
| **Total v1** | **36-52 weeks** | **Portfolio-Ready CLI** | |

---

## 2. Phase 0: Foundation (Weeks 1-2)

### Week 1: Environment Setup
| Day | Task | Done Criteria |
|-----|------|---------------|
| 1-3 | Install Java 21, Maven 3.9+, Docker Desktop, Git, IntelliJ IDEA | `java -version`, `mvn -v`, `docker --version`, `git --version` all work |
| 4-6 | Build first Spring Boot CRUD API with tests (follow Spring guides) | `mvn verify` passes, tests green |
| 7 | Create GitHub repo + multi-module engine layout + CI | GitHub repo exists, `mvn verify` in CI passes |

**Deliverables:**
- [ ] `docs/VERSIONS.md` with pinned versions
- [ ] `docs/DIARY.md` (symlink to `work/DAILY_LOG.md`)
- [ ] `docs/DECISIONS.md` with DEC-001 through DEC-013

### Week 2: First LLM Integration
| Day | Task | Done Criteria |
|-----|------|---------------|
| 8 | First LLM call from Java (Spring AI) | Console prints LLM response |
| 9 | Data model records + JSON round-trip tests | `mvn test` passes on engine-model |
| 10-11 | FakeModel + ModelRouter with failover test | Failover test passes |
| 12-13 | Clarifier for "REST CRUD for Book" → requirements.md | `requirements.md` written |
| 14 | Review diary, fix loose ends, tag `v0.0.1` | `git tag v0.0.1` |

**Gate 0 Checklist:**
- [ ] Java 21, Maven, Docker, Git installed & verified
- [ ] GitHub repo + CI (`mvn verify` on push)
- [ ] Spring Boot CRUD API + tests built independently
- [ ] First LLM call from Java works
- [ ] `VERSIONS.md`, `DIARY.md`, `DECISIONS.md` created

---

## 3. Phase 1: Core Engine (Weeks 3-38)

### Step 1.1: Data Model (engine-model) - Week 3
| Task | Done Criteria |
|------|---------------|
| Create records: Requirement, RequirementsDoc, PlanTask, Plan, Finding, Fix, RoundReport, Job | Records compile |
| Enums: Severity, Status, JobState, FixResult, ApprovalStatus, AuditAction | Enums compile |
| Fingerprint utility: `hash(tool + "|" + ruleId + "|" + filePath + "|" + codeLine)` | Unit test passes |
| JSON round-trip tests for all records | 100% pass |

**Files:** `engine-model/src/main/java/com/example/orchestrator/model/`

### Step 1.2: LLM Layer (engine-llm) - Weeks 4-5
| Task | Done Criteria |
|------|---------------|
| Spring AI config: Ollama, NVIDIA, OpenRouter providers | All 3 providers configure |
| ModelRouter: primary + fallbacks + circuit breaker (3 failures → 60s) | Failover test passes |
| Profiles: PLANNER, CODER, REVIEWER, DOCUMENTATION (config-driven) | Profile config loads |
| CostTracker: tokens → cost via price table, per-job budget enforcement | Budget enforcement test passes |
| FakeModel: canned responses for all profiles (tests without internet/cost) | Tests run offline, zero cost |

**Files:** `engine-llm/src/main/java/com/example/orchestrator/model/router/`

### Step 1.3: Clarifier (engine-core) - Week 6
| Task | Done Criteria |
|------|---------------|
| PLANNER prompt with checklist (entities, roles, auth, DB, validation, etc.) | Prompt template loads |
| Returns ≤8 questions as JSON (id, text, default) | JSON parses, ≤8 questions |
| Client sends answers → follow-up round if needed | Follow-up works |
| Produces RequirementsDoc v1 → writes `requirements.md` | File written, valid format |
| Test: "REST CRUD for books with login" → sensible questions | Manual test passes |

### Step 1.4: Prompt Refiner (engine-core) - Week 7
| Task | Done Criteria |
|------|---------------|
| Spring AI prompt template with 10 fixed sections | Template renders |
| Fixed stack/rules from config (not model) | Config values in prompt |
| Validation: all sections present, all req IDs referenced | Validator passes |
| Saves to `requirements.md` with version history | Version history maintained |

### Step 1.5: Planner (engine-core) - Week 8
| Task | Done Criteria |
|------|---------------|
| Input: requirements.md + refined prompt → TaskPlan | Plan generated |
| Task fields: id, title, files[], dependsOn[], requirementIds[], acceptance[] | All fields present |
| Validation: no cycles, all req IDs covered, relative paths | Validator passes |
| Topological sort for task order | Topological sort works |
| Typical order: Entities → Repos → Services → Controllers → Security → Tests → Docker → README | Order correct |
| Test: 5 samples pass all validations | 5/5 pass |

### Step 1.6: Skeleton Templates (engine-templates) - Week 9
| Task | Done Criteria |
|------|---------------|
| Spring Initializr baseline: Web, Validation, Data JPA, PostgreSQL, Flyway, Security, Actuator, Testcontainers | `mvn verify` passes |
| Hardening: pinned versions, Checkstyle, SpotBugs+FindSecBugs, JaCoCo, Dependency-Check plugins | Plugins configured |
| Package layout: controller, service, repository, domain, dto, mapper, config, security, exception | Structure correct |
| Extras: Global exception handler, Dockerfile, docker-compose.yml, GitHub Actions CI | Files exist |
| Copy to job workspace at build start | Copy works |
| **Verify**: Skeleton passes `mvn verify` + all scanners = 0 high findings | **Gate 1 prerequisite** |

### Step 1.7: Coder (engine-core) - Weeks 10-12
| Task | Done Criteria |
|------|---------------|
| Build prompt: task + plan/req excerpts + related files + conventions + security rules | Prompt builds correctly |
| Parse LLM tool calls (write/edit/read/list) with strict validation | Parser handles valid/invalid |
| File tools: path resolution (reject outside workspace), symlink check, size limits, .git/template protection | Security tests pass |
| Staging area → pre-check (secrets, dangerous patterns) → promote → compile in sandbox | Staging works |
| Compile in sandbox → retry 3x on fail → mark for round 2 | Retry logic works |
| Git commit after each task | Git commits created |
| **Test**: Builds 3-entity CRUD that compiles | **Project compiles** |

### Step 1.8: Sandbox (engine-sandbox) - Week 13
| Task | Done Criteria |
|------|---------------|
| Docker image: JDK + Maven + Scanners | Image builds |
| Limits: memory/CPU/process caps, time limit, non-root, no host mounts except workspace | Limits enforced |
| Maven cache volume (persistent) | Cache persists across runs |
| Java wrapper: `run(command, workspace, timeout)` → exitCode, stdout, stderr, duration | Wrapper works |
| Kill container on timeout | Timeout kills container |
| **Test**: Malicious project (read outside workspace, infinite loop) contained + killed | **Containment verified** |

### Step 1.9: Verifier (engine-verify) - Weeks 14-15
| Tool | Command | Output | Severity | Done |
|------|---------|--------|----------|------|
| Compile | `mvn -q compile` | Compiler errors | HIGH | ☐ |
| Tests | `mvn verify` | Surefire XML | HIGH (fail) | ☐ |
| Coverage | JaCoCo | XML | MEDIUM (< threshold) | ☐ |
| Static/Security | SpotBugs+FindSecBugs | XML | Per rule | ☐ |
| Code Style | Checkstyle | XML | LOW/MEDIUM | ☐ |
| Patterns | Semgrep (Java+Spring) | JSON | Per rule | ☐ |
| Vuln Deps | OWASP DepCheck + Trivy | JSON | Per CVE | ☐ |
| Secrets | Semgrep secrets / gitleaks | JSON | HIGH | ☐ |
| Custom | Spring rules (missing @Valid, open Actuator, etc.) | Custom | Per rule | ☐ |

**Parsers:** Unit tests with saved sample reports in `src/test/resources`
**Parallel:** Run independent scanners in parallel
**Output:** `VerificationResult` { buildOk, testsPassed, testsFailed, coverage, findings[] }
**Test**: 3 planted problems (SQLi, vuln dep, failing test) detected exactly

### Step 1.10: Five-Round Fix Loop (Weeks 16-18)
| Round | Action | Done Criteria |
|-------|--------|---------------|
| 1 (BUILD) | Coder all tasks → Verifier → Record all findings OPEN (firstSeenRound=1) | Findings recorded |
| 2-5 (FIX) | OPEN by severity (CRITICAL first) → group by file → Fixer → edits via staging → git commit | Fixes applied |
| | Full Verifier re-run each round | Re-verification works |
| | Fingerprint diff: Gone→FIXED, Still→OPEN, New→NEW | Diff correct |
| | Regression guard: new > fixed OR passing test fails → revert + retry once | Guard works |
| | Write `fixes.md` + `vulnerabilities.md` per round | Files written |

**Rules:** Minimum necessary change, keep existing tests, never delete tests to pass
**Stop Early**: No HIGH/CRITICAL + tests pass, OR no progress, OR budget spent
**Test**: 5 planted issues fixed without breaking tests, history correct in both .md files

### Step 1.11: Artifact Store (Weeks 19-20)
| Component | Done Criteria |
|-----------|---------------|
| Folder: `<project>/.companion/` with 5 .md files + `runs/round-N.json` | Folder structure correct |
| Formats: requirements.md, plan.md, fixes.md, vulnerabilities.md, report.md (stable) | Formats valid |
| DB: Jobs, Rounds, Findings, Fixes, Costs (Flyway V1, V2...) | Tables created, queries work |
| Test: Completed run produces all 5 files + DB matches | Files + DB match |

### Step 1.12: Evaluation Suite (eval/) - Weeks 21-22
| Task | Done Criteria |
|------|---------------|
| 30 Golden Specs: 10 CRUD, 10 Auth+Relations, 10 Business Rules | Specs written |
| Runner: CLI main → each spec → fixed answers → records CSV | Runner works |
| Summary table vs targets + previous run | Table prints |
| Run after every significant change | CI integration |
| Cheap/local model for quick runs, strong model for release | Model switching works |
| **Test**: Runner completes 10 specs, prints metrics table | **Metrics printed** |

### Step 1.13: Hardening (v0.1) - Weeks 23-24
| Hardening | Done Criteria |
|-----------|---------------|
| Prompt injection defense (strip instruction-like text) | Injection test passes |
| Secret scanning in prompts | Secrets detected |
| Logging (job-id correlation, no secrets) | Logs clean |
| Retries/timeouts everywhere, graceful shutdown | No hangs |
| Resume from last saved state after crash | Resume works |
| 70%+ unit test coverage in engine modules | Coverage ≥ 70% |

---

## Phase 2: CLI Application (Weeks 39-50)

### Step 2.1: CLI App (app-cli) - Weeks 39-42
| Task | Done Criteria |
|------|---------------|
| Create app-cli (picocli + Spring Boot) | Module builds |
| Commands: generate, status, approve, verify, fix, report, config, doctor | All commands work |
| Terminal UX: progress line, questions with defaults, flags (--yes, --json, --offline) | UX polished |
| Config file (`~/.companion/config.yml`) + env var keys | Config works |
| Integration tests (FakeModel), GitHub Actions release workflow | Tests pass |

### Step 2.2: Terminal UX Polish - Weeks 43-44
| Feature | Done Criteria |
|---------|---------------|
| Live progress line per stage with round number + elapsed time | Progress renders |
| Questions with suggested defaults (Enter accepts) | Defaults work |
| Flags: `--yes`, `--max-rounds`, `--budget`, `--model`, `--offline`, `--json` | All flags work |
| `companion doctor` diagnoses missing prerequisites | Doctor works |
| Clear error messages: "Docker not running: start Docker Desktop" | Errors actionable |

### Step 2.3: Release & Distribution - Weeks 45-46
| Task | Done Criteria |
|------|---------------|
| Executable JAR + launchers (Windows/Mac/Linux) | All launchers work |
| GraalVM native image (optional, later) | Optional |
| GitHub Actions release workflow (on tag) | Release workflow works |
| Install instructions per OS, Homebrew formula (later) | Docs complete |

### Gate 2 Checklist
- [ ] Stranger installs jar from README → completes project
- [ ] `companion doctor` correctly diagnoses missing prerequisites
- [ ] Feedback from ≥10 Java developers (record in `PROGRESS.md`)

---

## 4. Phase Dependencies & Critical Path

```
Phase 0 (2 wks)
    │
    ▼
Phase 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7 → 1.8 → 1.9 → 1.10 → 1.11 → 1.12 → 1.13
    │                                                         │
    └────────────────── CRITICAL PATH ──────────────────────┘
                              │
                              ▼
                        Phase 2 (CLI)
```

**Critical Path**: 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6 → 1.7 → 1.8 → 1.9 → 1.10 → 1.11 → 1.12 → 1.13
**Blockers**: 
- 1.6 (Skeleton) must pass `mvn verify` + scanners before 1.7
- 1.8 (Sandbox) needed before 1.7 can compile in sandbox
- 1.9 (Verifier) needed before 1.10 (Fix Loop)

---

## 5. Resource Allocation (Solo Dev, 15-20 hrs/week)

| Phase | Weeks | Hours/Week | Total Hours | Buffer |
|-------|-------|------------|-------------|--------|
| Phase 0 | 2 | 15 | 30 | 20% |
| Phase 1 | 22 | 15 | 330 | 20% |
| Phase 2 | 12 | 15 | 180 | 20% |
| **Total** | **36** | | **540** | **20%** |

**With 20% buffer**: ~43 weeks (≈10 months)

---

## 6. Risk Register

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| LLM API changes break prompts | Medium | High | Pin model versions, version prompts, fallback models |
| Docker not available on user machine | High | High | `companion doctor` check, clear error message |
| LLM costs exceed budget | Low | High | Hard budget enforcement, local Ollama fallback |
| Fix loop infinite regression | Medium | High | Regression guard (revert if new > fixed) |
| Sandbox escape vulnerability | Low | Critical | Non-root, read-only fs, no host mounts, resource caps |
| Spring Boot/Spring AI version conflicts | Medium | Medium | Pin versions in VERSIONS.md, test on upgrade |
| Scanner false positives | High | Medium | Severity mapping config, ACCEPTED status |
| LLM returns malformed tool calls | Medium | High | Strict parsing, retry 3x, escalate to user |
| Spring Boot 3.x breaking changes | Low | High | Pin version, test skeleton after upgrade |

---

## 6. Definition of Done (Per Task)

| Checklist Item | Required |
|----------------|----------|
| Code compiles (`mvn compile`) | ✅ |
| Unit tests pass (`mvn test`) | ✅ |
| Integration tests pass (if applicable) | ✅ |
| `mvn verify` passes (compile + test + checkstyle + spotbugs) | ✅ |
| Unit test coverage ≥ 70% (engine modules) | ✅ |
| Documentation updated (JavaDoc + README if public API) | ✅ |
| `DECISIONS.md` updated if architectural choice made | ✅ |
| `DAILY_LOG.md` updated end of day | ✅ |
| `PROGRESS.md` checkboxes updated | ✅ |
| `BLOCKERS.md` updated if blocked | ✅ |
| `DECISIONS.md` updated if architectural decision | ✅ |

---

*This implementation plan is the execution roadmap. Update `PROGRESS.md` daily. All Gates are hard requirements.*