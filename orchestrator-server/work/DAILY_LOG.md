# Daily Build Log

> One paragraph per day. Write at end of each session. Saves you when you return after a break.

---

## 2026-10-26
**Phase:** 0 | **Step:** Planning | **Hours:** 2
**What:** Created comprehensive orchestrator plan (ORCHESTRATOR_PLAN.md) with realistic v1 scope for beginner (6-12 months). Reduced original 6-phase/40-week plan to 2-phase/12-month v1. Created work tracking folder structure.
**Blockers:** None
**Decisions:** Cut MCP, Web, IDE, Mobile from v1. Focus on CLI that generates one Spring Boot project type (REST CRUD + JPA + Validation + Tests).
**Tomorrow:** Install tools (Java 21, Maven, Docker, Git, IntelliJ). Verify all versions. Create GitHub repo.

---

## 2026-10-27
**Phase:** 0 | **Step:** 0.2-0.3 | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** 

---

## 2026-10-28
**Phase:** 1 | **Step:** 1.2-1.13 | **Hours:** 28
**What:** Completed Phase 1.2 LLM Layer (engine-llm) with all 13 tests passing. Implemented Phase 1.3 Clarifier (engine-core) with 6 tests passing. Implemented Phase 1.4 Prompt Refiner (engine-core) with 6 tests passing. Implemented Phase 1.5 Planner (engine-core) with 6 tests passing. Implemented Phase 1.6 Skeleton Templates (engine-templates) with 8 tests passing. Implemented Phase 1.7 Coder (engine-core) with 7 tests passing. Implemented Phase 1.8 Sandbox (engine-sandbox) with 6 tests passing. **Completed Phase 1.9 Verifier (engine-verify)** with 7 tests passing. **Completed Phase 1.10 Five-Round Fix Loop (engine-core)** with FixLoop, Fixer, FixLoopConfig, FixerConfig. **Completed Phase 1.11 Artifact Store (engine-store)** with ArtifactStore service, Flyway migrations V1/V2, JPA entities (Job, Round, Finding, Cost), Spring Data JPA repositories, file artifact writing (requirements.md, plan.md, fixes.md, vulnerabilities.md, report.md + runs/). **Completed Phase 1.12 Evaluation Suite (eval/)** with EvaluationRunner, SpecLoader, CsvReportWriter, 4 golden specs (CRUD-001, CRUD-002, AUTH-001, BIZ-001), EvaluationSummary with success rate, quality rate, cost, coverage. **Completed Phase 1.13 Hardening (v0.1)** with: PromptSanitizer (20+ injection patterns, allowed-list for legitimate uses), SecretScanner (25+ patterns for AWS, GitHub, Slack, Stripe, JWT, etc., high-entropy fallback, redaction), CorrelationLogger with MDC (jobId, round, taskId, specId), RetryPolicy with exponential backoff, timeout, configurable predicates, StateManager with JSON snapshots (.companion/state/), canResume(), getLastCompletedRound(). Coder: Builds prompt with task + plan/req excerpts + conventions + security rules, parses LLM tool calls (writeFile, editFile, readFile, listFiles), FileTools with staging area (path validation, size limits, symlink checks, .git/.template protection), staging→promote→compile in sandbox with mvn compile retry (3x). TemplateEngine: 13 template files (pom.xml, Application.java, application.yml, Dockerfile, docker-compose.yml, .gitignore, README.md, SecurityConfig.java, GlobalExceptionHandler.java, JwtAuthenticationFilter.java, checkstyle.xml, spotbugs-exclude.xml, ci.yml), 9 packages, hardening plugins, GitHub Actions CI. Sandbox: Docker image with JDK 21, Maven, Trivy, Semgrep, DepCheck, SpotBugs, Checkstyle; resource limits (memory, CPU, pids); Maven cache volume; timeout enforcement; non-root user; workspace mount. Verifier: 9 scanners (CompileScanner, TestScanner, CoverageScanner, SpotBugsScanner, CheckstyleScanner, SemgrepScanner, DepCheckScanner, TrivyScanner, SecretsScanner) with parsers for each tool output format; Finding model with SHA-256 fingerprint for deduplication; Severity normalization (CRITICAL > HIGH > MEDIUM > LOW > INFO) via config mapping; VerificationRunner with parallel execution (config.maxParallelScanners); VerificationResult with buildOk, testsPassed, coveragePercent, findings[], duration. FixLoop: Round 1 runs Coder tasks → Verifier → records OPEN findings; Rounds 2-5 filter OPEN by severity → group by file → Fixer proposes edits via LLM (REVIEWER profile) → FileTools stages edits → promote to workspace → full Verifier re-run → fingerprint diff (Gone→FIXED, Still→OPEN, New→NEW) → regression guard (new > fixed → revert) → early stop (no HIGH/CRITICAL + tests pass OR no progress). ArtifactStore: File artifacts in .companion/<project>/<jobId>/ (requirements.md, plan.md, fixes.md, vulnerabilities.md, report.md + runs/round-N.json); Database with Flyway V1 (jobs, rounds, findings, costs) and V2 (project_memory, approvals); JPA entities + Spring Data repositories; 8 tests passing. Evaluation: SpecLoader (YAML/JSON), EvaluationRunner (parallel, mock), CsvReportWriter, 4 golden specs (CRUD-001, CRUD-002, AUTH-001, BIZ-001), 4 tests passing. All tests passing: 99 total (28 model + 13 llm + 25 core + 6 sandbox + 8 templates + 7 verify + 8 store + 4 eval).
**Blockers:** None
**Decisions:** Record accessor methods use property name directly. Test config uses explicit @SpringBootTest with CoreAutoConfiguration + TestConfig. Validation checks keywords from requirements. Planner/Coder/Clarifier parsing falls back to generated content if JSON parsing fails. Coder compilation step skipped in tests (no mvn in test env). Sandbox tests use mocked Docker client (no Docker daemon required). Verifier tests mock SandboxService entirely (no Docker dependency in CI). FixLoop beans are conditional on VerificationRunner bean (only created in engine-verify context). ArtifactStore tests use H2 in-memory with Flyway migrations. Evaluation tests use plain ObjectMapper with JSR310 module for Instant. Hardening components integrated via HardeningAutoConfiguration, test configs updated with mock beans.
**Tomorrow:** Run coverage report (jacoco) to verify 70%+ test coverage, then begin Phase 2 (CLI Application).

---

## 2026-10-29
**Phase:** 0 | **Step:** 0.6 | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** 

---

## 2026-10-30
**Phase:** 0 | **Step:** 0.7 | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** 

---

## 2026-10-31
**Phase:** 0 | **Step:** 0.8 | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** 

---

## 2026-11-01
**Phase:** 0 | **Step:** 0.9 | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** 

---

## 2026-11-02
**Phase:** 0 | **Step:** Review | **Hours:** 
**What:** 
**Blockers:** 
**Decisions:** 
**Tomorrow:** Gate 0 review - verify all Gate 0 criteria met

---

*Template: Copy the above block for each new day. Keep entries to one paragraph. Note blockers immediately.*