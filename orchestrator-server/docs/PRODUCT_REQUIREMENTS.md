# Product Requirements Document (PRD)
## Java Companion Orchestrator - v1 (CLI MVP)

---

## 1. Product Overview

### 1.1 Product Name
**Java Companion Orchestrator** (CLI: `companion`)

### 1.2 Vision Statement
> A CLI tool that generates a production-ready Spring Boot REST API project from a natural language description, with built-in security scanning, testing, and auto-fixing — delivering a verified, audit-ready project in under 10 minutes.

### 1.3 Target Users (v1)
| Primary | Secondary |
|---------|-----------|
| **Java Tech Lead** at regulated mid-size company (8-30 engineers) | Solo Java freelancer building Spring Boot APIs |
| Ships on schedule, passes audits, fears AI PR rubber-stamping | Wants speed but can't trust AI output for client work |

### 1.4 Core Problem
Java backend developers use AI to generate code but face:
- **Untrustworthy output**: Security vulnerabilities, failing tests, style violations
- **Review bottleneck**: AI generates PRs faster than humans can review
- **Audit/compliance risk**: No verifiable trail of what was checked/fixed

### 1.5 Success Metrics (v1 Gate 1 Targets)
| Metric | Target | Measurement |
|--------|--------|-------------|
| Compile success (round 1) | ≥ 90% | 10 golden specs |
| Tests pass (after round 5) | ≥ 85% | 10 golden specs |
| HIGH/CRITICAL left (round 5) | 0 on ≥ 8/10 specs | 10 golden specs |
| Regressions introduced | < 5% | Fixed vs New ratio |
| Cost per project | Ceiling tracked | Track per run |
| **Paid pilots → retainers** | **≥ 3/5** | Pilot conversion rate |

---

## 2. User Personas

### Primary: Java Tech Lead (Regulated Mid-Size Co)
- **Title**: Engineering Manager / Tech Lead
- **Company**: 8-30 engineers, regulated (bank, insurer, healthcare)
- **Pain**: AI PRs arrive faster than review capacity; audit fear; rubber-stamping risk
- **Goal**: Ship on schedule, pass audits, sleep at night
- **Workflow**: `companion generate "..."` → review plan → approve → get verified project + audit trail

### Secondary: Solo Java Freelancer
- **Pain**: Can't trust AI output for client deliverables
- **Goal**: Deliver production-ready Spring Boot APIs fast
- **Workflow**: Same as primary, but solo; values `companion verify` on existing projects

---

## 3. User Journeys (v1)

### Primary: Generate New Project
```
$ companion generate "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" --name library
✓ Clarifying questions asked (5-8 questions with defaults)
✓ Requirements.md generated
✓ Plan.md generated with traceability
✓ User approves
✓ Spring Boot skeleton copied
✓ Coder generates: Entity, Repository, Service, Controller, DTOs, Tests, Dockerfile
✓ Round 1: Build + Verify (compile, test, security scan)
✓ Rounds 2-5: Auto-fix findings (security, test failures, style)
✓ Final: Working Spring Boot project + reports
```

### Secondary: Verify Existing Project
```
$ companion verify ./my-spring-project
✓ Runs all scanners (Semgrep, Trivy, DepCheck, SpotBugs, Checkstyle, JaCoCo, SecretScan)
✓ Runs tests
✓ Produces vulnerabilities.md + fixes.md + report.md
```

### Secondary: Fix Existing Project
```
$ companion fix ./my-project --max-rounds 3
✓ Runs verifier, applies fixes, re-verifies up to 3 rounds
✓ Produces fixes.md + updated vulnerabilities.md
```

---

## 3. Functional Requirements (v1)

### FR-1: Clarification Flow
- **FR-1.1**: Accept natural language request via CLI argument or stdin
- **FR-1.2**: Generate ≤8 clarifying questions with sensible defaults (JSON)
- **FR-1.3**: Accept user answers (interactive or `--answers-file`)
- **FR-1.4**: Produce `requirements.md` v1 with: original request, Q&A, assumptions, refined prompt

### FR-2: Planning
- **FR-2.1**: Generate ordered `TaskPlan` from requirements + refined prompt
- **FR-2.2**: Each task: id, title, files[], dependsOn[], requirementIds[], acceptance[]
- **FR-2.3**: Validate: no cycles, all req IDs covered, relative paths, topological sort
- **FR-2.4**: Output `plan.md` with traceability table (requirement → tasks)

### FR-3: Approval Gate
- **FR-3.1**: Display plan summary + traceability table
- **FR-3.2**: Prompt for approval (y/N) with `--yes` flag for automation
- **FR-3.3**: On reject: exit gracefully; on approve: proceed to execution

### FR-4: Code Generation (Coder)
- **FR-4.1**: Execute tasks in dependency order
- **FR-4.2**: Per task: build prompt → LLM (CODER) → tool calls (write/edit/read/list)
- **FR-4.3**: File tools: path resolution (reject outside workspace), symlink check, size limits
- **FR-4.4**: Staging area → pre-check (secrets, dangerous patterns) → promote → compile in sandbox
- **FR-4.5**: Compile in sandbox → retry 3x on fail → mark for round 2

### FR-5: Verification (5-Round Loop)
- **FR-5.1**: Round 1: Build + full verification → record all findings as OPEN (firstSeenRound=1)
- **FR-5.2**: Rounds 2-5: OPEN findings by severity → group by file → Fixer → edits via staging → git commit
- **FR-5.3**: Full verifier re-run each round
- **FR-5.4**: Fingerprint diff: Gone→FIXED, Still→OPEN, New→NEW
- **FR-5.5**: Regression guard: new > fixed OR passing test fails → revert + retry once
- **FR-5.6**: Stop early: no HIGH/CRITICAL + tests pass, OR no progress, OR budget spent
- **FR-5.7**: Write `fixes.md` + `vulnerabilities.md` per round

### FR-6: Verification Tools (7 Scanners)
| Tool | Command | Findings → Severity |
|------|---------|---------------------|
| Compile | `mvn -q compile` | Compiler errors → HIGH |
| Tests | `mvn verify` | Surefire XML → HIGH (fail) |
| Coverage | JaCoCo | XML → MEDIUM (< threshold) |
| Static/Security | SpotBugs + FindSecBugs | XML → Per rule |
| Code Style | Checkstyle | XML → LOW/MEDIUM |
| Patterns | Semgrep (Java+Spring) | JSON → Per rule |
| Vuln Deps | OWASP DepCheck + Trivy | JSON → Per CVE |
| Secrets | Semgrep secrets / gitleaks | JSON → HIGH |

### FR-6: Five-Round Fix Loop
- **Round 1**: BUILD (coder → verifier)
- **Rounds 2-5**: FIX (group by file → fixer → verify → fingerprint diff)
- **Stop Early**: No HIGH/CRITICAL + tests pass, OR no progress, OR budget spent
- **Rules**: Minimum necessary change, keep existing tests, never delete tests to pass

### FR-7: Artifact Store
- **Folder**: `<project>/.companion/`
- **Files**: `requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md`, `runs/round-N.json`
- **DB**: Jobs, Rounds, Findings, Costs (Flyway V1, V2...)

### FR-8: CLI Commands (v1)
| Command | Description |
|---------|-------------|
| `companion generate "spec" --name <name>` | Generate new project |
| `companion verify <folder>` | Run scanners + tests on existing project |
| `companion fix <folder> [--max-rounds N]` | Run fix rounds on existing project |
| `companion status <job-id>` | Show job state |
| `companion approve <job-id>` | Approve pending plan |
| `companion report <job-id>` | Generate final report |
| `companion config <get|set> <key> <value>` | Manage config |
| `companion doctor` | Diagnose prerequisites (Java, Docker, keys) |

### FR-9: Security Rules (Enforced by Verifier)
| ID | Rule | Enforcement |
|----|------|-------------|
| SEC-1 | Constructor injection only | ArchUnit |
| SEC-2 | No SQL concatenation | Semgrep |
| SEC-3 | BCrypt/Argon2 for passwords | SpotBugs |
| SEC-4 | SecureRandom for tokens | Semgrep |
| SEC-5 | No MD5/SHA1/DES/RC4/ECB | SpotBugs/Semgrep |
| SEC-6 | @Valid on @RequestBody | ArchUnit |
| SEC-6 | No secrets in code/logs | SecretScan |
| SEC-8 | CORS allow-list only | Semgrep |
| SEC-9 | Rate limiting on auth | Semgrep |
| SEC-10 | Actuator health only public | ArchUnit |

### FR-10: Non-Functional Requirements
| Category | Requirement |
|----------|-------------|
| **Performance** | End-to-end < 10 min for typical CRUD project |
| **Reliability** | Resume from last saved state after crash |
| **Security** | Sandbox: Docker, non-root, no host mounts, no secret env vars |
| **Cost Control** | Per-job budget (tokens → $), hard stop at ceiling |
| **Portability** | Runs on Linux/macOS/Windows (via Docker) |
| **Offline Mode** | `--offline` flag uses local Ollama model |

---

## 4. Non-Goals (v1 Explicit Exclusions)

| Feature | Deferred To |
|---------|-------------|
| MCP Server / Agent Plugin | v1.1+ |
| Web Application (REST API + React UI) | v2 |
| IntelliJ Plugin | v2 |
| Mobile App (React Native) | v2+ |
| Multi-language support (Python, Go, Node) | v2+ |
| Web dashboard / team features / billing | v2+ |
| Advanced scheduling / multi-project workspaces | v2+ |

---

## 5. Acceptance Criteria (v1 Release)

| Criterion | Verification |
|-----------|--------------|
| Stranger downloads JAR, runs `companion generate "REST CRUD for Book" --name library`, answers 5 questions, approves plan, gets working Spring Boot project | Manual test with fresh machine |
| Project compiles, passes tests, zero HIGH/CRITICAL security findings | `mvn verify` + scanner reports |
| All 5 artifact files generated (`requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md`) | File existence + format validation |
| Cost tracking accurate (within 10% of actual API billing) | Compare `CostTracker` vs provider billing |
| Stranger completes flow in < 10 minutes | Stopwatch test |

---

## 6. Out of Scope (Explicit)

- Non-Java languages
- Web dashboard / multi-user / billing
- MCP server (separate Phase 3)
- IDE plugins (separate Phase 5)
- Mobile app (Phase 6)
- Multi-project workspaces
- Team billing / SSO / RBAC

---

*This PRD is the source of truth for v1 scope. All implementation decisions must trace back to these requirements.*