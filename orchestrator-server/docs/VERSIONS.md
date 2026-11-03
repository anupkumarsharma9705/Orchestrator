# Pinned Versions

> Update when upgrading. Pin to specific patch versions. No ranges. No SNAPSHOT.

---

## Core Runtime
| Component | Version | Updated | Notes |
|-----------|---------|---------|-------|
| Java | 21.0.x (LTS) | 2026-10-26 | Eclipse Temurin or Oracle JDK |
| Maven | 3.9.x | 2026-10-26 | Wrapper (mvnw) |
| Spring Boot | 3.4.x | 2026-10-26 | Check spring.io/projects/spring-boot |
| Spring AI | 1.0.x | 2026-10-26 | Check spring.io/projects/spring-ai |

## Database & Migration
| Component | Version | Updated | Notes |
|-----------|---------|---------|-------|
| PostgreSQL | 16.x | 2026-10-26 | Production (via Docker) |
| H2 Database | 2.2.x | 2026-10-26 | Test profile only |
| SQLite | 3.46.x | 2026-10-26 | Production file-based |
| Flyway | 10.x | 2026-10-26 | Migrations |

## Security & Quality Scanners
| Scanner | Version | Updated | Notes |
|---------|---------|---------|-------|
| Semgrep | 1.100.x | 2026-10-26 | Java + Spring rule sets |
| Trivy | 0.55.x | 2026-10-26 | Filesystem + image scan |
| OWASP Dependency-Check | 9.x | 2026-10-26 | NVD API key for speed |
| SpotBugs | 4.8.x | 2026-10-26 | With Find Security Bugs plugin |
| Checkstyle | 10.x | 2026-10-26 | Google checks + custom |
| JaCoCo | 0.8.x | 2026-10-26 | Coverage |

## Build & Test
| Tool | Version | Updated | Notes |
|------|---------|---------|-------|
| Docker | 27.x | 2026-10-26 | Desktop with WSL2 integration |
| Testcontainers | 1.19.x | 2026-10-26 | PostgreSQL module |
| ArchUnit | 1.2.x | 2026-10-26 | Architecture tests |
| JUnit | 5.10.x | 2026-10-26 | Jupiter + Params |
| Mockito | 5.12.x | 2026-10-26 | Inline mock maker |
| AssertJ | 3.25.x | 2026-10-26 | Fluent assertions |
| picocli | 4.7.x | 2026-10-26 | CLI parsing |

## CLI & Deployment
| Component | Version | Updated | Notes |
|-----------|---------|---------|-------|
| GraalVM | 21.x (optional) | - | Native image for CLI (later) |
| Node.js | 20.x LTS | 2026-10-26 | For web-ui (Phase 4) |
| npm | 10.x | 2026-10-26 | Package manager |

---

## Update Protocol

1. **Check** official sites monthly for security patches
2. **Test** in branch: update `VERSIONS.md` → `mvn verify` → run eval suite
3. **Merge** only if: all tests pass, eval metrics ≥ previous run
4. **Record** in `DECISIONS.md` with version bump rationale

---

## Current LLM Provider Config (v1)

| Provider | Model | Profile | Status |
|----------|-------|---------|--------|
| NVIDIA | nemotron-3-ultra | PLANNER, CODER, REVIEWER, DOCUMENTATION | Primary |
| NVIDIA | nemotron-3-ultra | All | Fallback (same) |
| Ollama | codellama:13b | All | Local fallback (Phase 1.1) |
| OpenRouter | various | All | Future (v1.1) |

> **Note:** v1 uses NVIDIA only. Ollama added as local fallback in Phase 1.1. OpenRouter in v1.1.