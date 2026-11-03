# Architecture Decisions Log

> Record every architectural decision with rationale. Reference in code comments.

---

## DEC-001: v1 Scope Reduction (2026-10-26)
**Decision:** Cut MCP, Web, IDE, Mobile, Multi-language from v1. Build only CLI + Core Engine.
**Rationale:** Original 6-phase/40-week plan impossible for beginner. 6-12 month v1 achievable.
**Impact:** Portfolio piece deliverable in 6-12 months. Business features deferred to v2+.
**Reversibility:** Can add back in v2+ (MCP → CLI polish → Web → IDE → Mobile → Multi-lang)
**Related:** DEC-002, DEC-003

---

## DEC-002: Single Project Type for v1 (2026-10-26)
**Decision:** Support ONLY "REST CRUD + JPA + Validation + Tests" project type in v1.
**Rationale:** Reduces Planner/Coder complexity exponentially. One skeleton template. One plan structure.
**Impact:** Coder only needs to handle known patterns. Easier to test. Easier to verify.
**Future:** Add project types via template registry in v2.

---

## DEC-003: Single LLM Provider for v1 (NVIDIA Nemotron-3-Ultra)
**Decision:** Use NVIDIA Nemotron-3-Ultra as primary, no multi-provider failover in v1.
**Rationale:** Simplifies ModelRouter. NVIDIA API is fast, free tier available. Reduces config complexity.
**Trade-off:** No failover in v1. Add OpenRouter/Ollama in v1.1.
**Config:** `orchestrator.model-router.providers.nvidia.enabled=true`, others `false`.

---

## DEC-004: SQLite for Production, H2 for Tests
**Decision:** SQLite file-based DB for production, H2 in-memory for tests.
**Rationale:** Zero-config, file-based, no separate server. Flyway works identically.
**Trade-off:** Not horizontally scalable. Fine for single-user/small-team tool.

---

## DEC-005: SQLite Artifact Store + Filesystem for Reports
**Decision:** SQLite for jobs/rounds/findings/costs. Filesystem (`.companion/`) for human-readable reports.
**Rationale:** Structured queries via SQL, human-readable via Markdown. Best of both.
**Format Stability:** File formats frozen at v1.0. Changes = version bump + migration note.

---

## DEC-005: Circular Dependency Fix - @Lazy on ProviderHealthController
**Decision:** Add `@Lazy` to `ModelRouter` in `ProviderHealthController` constructor.
**Rationale:** `ModelRouter` → `ProviderHealthController` → `ModelRouter` circular dependency.
**Alternative Considered:** Interface extraction - rejected as over-engineering for v1.

---

## DEC-006: Test Profile Uses "test" Provider (Not NVIDIA)
**Decision:** `application-test.yml` defines `default-provider: test` with fully configured test provider.
**Rationale:** Tests must run without API keys. Test provider returns canned responses.
**Implementation:** `TestModelProviderConfig` @Profile("test") provides test ModelProvider bean.

---

## DEC-007: Five-Round Fix Loop with Early Stop
**Decision:** Round 1 = Build, Rounds 2-5 = Fix. Stop early if: no HIGH/CRITICAL + tests pass, OR no progress, OR budget spent.
**Rationale:** Prevents infinite loops. Budget protection. Diminishing returns after round 3.
**Rule:** Minimum necessary change, keep existing tests, never delete tests to pass.

---

## DEC-007: FakeModel for All Testing
**Decision:** `FakeModel` implements `ModelProvider` with canned responses. Used in ALL tests.
**Rationale:** Zero cost, zero latency, deterministic, works offline. No API keys in CI.
**Rule:** Never call real LLM in tests. Ever.

---

## DEC-008: Sandbox = Docker Container Per Job
**Decision:** One Docker container per job. Limits: memory/CPU/time, non-root, no host mounts except workspace.
**Rationale:** Generated code is untrusted. Prompt injection, malicious code, infinite loops must be contained.
**Network:** Maven cache volume persistent. Network allowed for Maven download (v1). Later: repository proxy.

---

## DEC-009: Fingerprint = Hash(tool + ruleId + filePath + codeLine)
**Decision:** Finding fingerprint = SHA256(tool + "|" + ruleId + "|" + filePath + "|" + trimmedCodeLine)
**Rationale:** Survives line number changes. Same finding across rounds = same fingerprint.
**Usage:** Track FIXED/OPEN/NEW/REGRESSED across rounds.

---

## DEC-010: Security Rules = MUST Rules in Verifier
**Decision:** Security rules (SEC-1 through SEC-14) are MUST level. Violation = Finding (tool=rulecheck, severity=HIGH).
**Enforcement:** ArchUnit, Checkstyle, SpotBugs+FindSecBugs, Semgrep custom rules.
**Rule Format:** `rules.yml` with id, level (MUST/SHOULD), text, enforce[].

---

## DEC-011: Cost Tracking Per Job + Budget Enforcement
**Decision:** `CostTracker` counts tokens per call → cost via price table. Per-job budget (config). Exceed = job FAILED (BUDGET).
**Rationale:** Prevent runaway costs. User sets ceiling. Hard stop protects wallet.

---

## DEC-011: CLI Uses picocli + Spring Boot
**Decision:** `picocli` for command parsing, Spring Boot for DI. Executable jar + launchers.
**Rationale:** picocli mature, supports subcommands, help, completion. Spring Boot for engine integration.

---

## DEC-012: Artifact File Formats Frozen at v1.0
**Decision:** `requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md` formats stable.
**Rationale:** Web UI, IDE plugin, Mobile app will read these. Breaking change = version bump + migration.

---

## DEC-013: No Lombok in Generated Code (v1)
**Decision:** Generated projects use records + explicit constructors. No Lombok.
**Rationale:** Annotation processing can confuse generation/fixes. Records are standard in Java 21+.

---

## DEC-012: Spring Security deny-by-default + JWT (Future)
**Decision:** v1 uses simple API key. JWT/OAuth deferred to post-v1.
**Rationale:** Keeps v1 simple. Security rules enforced by Verifier, not runtime auth.

---

## DEC-013: Single Build Tool (Maven Only)
**Decision:** No Gradle support in v1. Maven wrapper (`mvnw`) only.
**Rationale:** Reduces template complexity. One build file to maintain.

---

## DEC-013: Checkstyle + SpotBugs + Semgrep as Code Quality Gates
**Decision:** Generated projects MUST pass Checkstyle, SpotBugs+FindSecBugs, Semgrep with 0 HIGH findings.
**Rationale:** Enforces code quality rules automatically. Part of Verifier pipeline.

---

## Decision Template (Copy for new decisions)

```markdown
## DEC-XXX: Short Title (YYYY-MM-DD)
**Decision:** One sentence what was decided.
**Rationale:** Why this choice over alternatives.
**Trade-offs:** What you gave up.
**Reversibility:** Easy/Hard/Impossible to change later.
**Related:** DEC-XXX, DEC-YYY
```