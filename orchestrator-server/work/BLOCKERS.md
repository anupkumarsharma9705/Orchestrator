# Blocker Tracker

> Log every blocker immediately. Update daily. Unblock = remove from active, move to resolved.

---

## 🔴 Active Blockers

| ID | Date | Phase/Step | Blocker Description | Severity | Owner | Status | Resolved |
|----|------|------------|---------------------|----------|-------|--------|----------|
| B-001 | 2026-10-26 | 0.1 | Need to learn Java/Spring/Docker basics before Phase 1 | High | Self | Active | |
| B-002 | 2026-10-26 | 0.2 | Docker Desktop WSL2 integration on Windows | Medium | Self | Pending | |
| B-003 | | | | | | | |
| B-004 | | | | | | | |
| B-005 | | | | | | | |

---

## ✅ Resolved Blockers

| ID | Date | Phase/Step | Blocker Description | Resolution | Resolved Date |
|----|------|------------|---------------------|------------|---------------|
| | | | | | |

---

## 🟡 Potential Future Blockers (Watch List)

| Risk | Phase | Mitigation Plan |
|------|-------|-----------------|
| Spring AI API changes frequently | 1.2 | Pin version in VERSIONS.md, test after each update |
| Spring Boot 3.x breaking changes | 1.6 | Use Spring Boot BOM, test skeleton after each upgrade |
| Docker not available in CI | 1.8 | Use GitHub Actions with Docker-in-Docker or self-hosted runner |
| LLM API rate limits / costs | 1.2, 1.7 | FakeModel for tests, budget enforcement, local Ollama fallback |
| Docker sandbox escape vulnerability | 1.8 | Non-root user, no host mounts, resource limits, read-only root fs |
| Spring AI structured output parsing failures | 1.2 | Retry once with error message, fallback to FakeModel in tests |
| SpotBugs/Semgrep false positives | 1.9 | Severity mapping config, allow ACCEPTED status with reason |
| Fix loop infinite regression | 1.10 | Regression guard (revert if new > fixed), max 5 rounds |
| Database migration conflicts | 1.11 | Flyway baseline on migrate, never edit applied migrations |
| LLM provider API changes | 1.2 | Abstract ModelProvider interface, versioned provider configs |

---

## Blocker Template (Copy for new blockers)

```markdown
| B-XXX | YYYY-MM-DD | X.X | Description | High/Medium/Low | Self | Active | |
```