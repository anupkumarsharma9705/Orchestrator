# Comprehensive Issue Tracker - All Reviews Combined

**Generated:** 2026-10-30  
**Scope:** All findings from QA, Security Review, Design Review, Investigate, and CSO audits  
**Total Issues:** 67 unique findings across 5 review types  

---

## Legend
- ✅ **DONE** - Fixed and verified
- 🔄 **IN PROGRESS** - Work started, not complete
- ❌ **NOT DONE** - Not yet addressed
- ⚠️ **WONT FIX** - Consciously deferred
- ❓ **UNVERIFIED** - Status unknown

---

## CRITICAL Issues (Must Fix Before Release)

| # | Issue | Source | Description | Location | Status | Evidence |
|---|-------|--------|-------------|----------|--------|----------|
| C-01 | Server Architecture Mismatch | QA, Investigate, CSO | `orchestrator` module builds as CLI (`WebApplicationType.NONE`) but contains REST controllers; server cannot start | `orchestrator/pom.xml:167`, `OrchestratorCli.java:40` | ✅ DONE | Split into `orchestrator-server` + `app-cli` modules |
| C-02 | No Offline Mode | QA, Investigate, CSO | All CLI commands require running REST server on :8080; `--dry-run` fails without server | `GenerateCommand.java:86-99`, `VerifyCommand.java:55-65` | ✅ DONE | Added `--offline` flag + `OfflineModeService` |
| C-03 | Empty Container Logs | QA, Investigate, CSO | `getContainerLogs()` returns empty strings; debugging failed builds impossible | `SandboxService.java:458-468` | ✅ DONE | Implemented `LogContainerResultCallback` with `CountDownLatch` |
| C-04 | All REST Endpoints Public | Security Review, CSO | No `@PreAuthorize`, `ApiKeyAuthService.isConfigured()` returns `false`; approval bypass via `POST /api/approvals/{id}/approve` | `TaskController.java`, `ProjectController.java`, `ApprovalController.java`, `CostTrackingController.java` | ✅ DONE | Added `@PreAuthorize`, `SecurityConfig`, `ApiKeyAuthService` |
| C-05 | No TLS on CLI↔Server | Security Review, CSO | `WebClient` built without SSL context; plaintext API keys, prompts, code | `OrchestratorClient.java:35-39` | ❌ NOT DONE | Need SSL config for WebClient and server |
| C-06 | Docker Socket Exposure | Security Review, CSO, Investigate | DoctorCommand checks port 2375 (unencrypted); sandbox mounts `unix:///var/run/docker.sock` with `CapAdd.CHOWN,FOWNER` | `DoctorCommand.java:127`, `SandboxConfig.java:14`, `SandboxService.java:404` | 🔄 IN PROGRESS | Doctor fixed; capabilities reduced |

## HIGH Severity (Exploitable / Data Loss)

| # | Issue | Source | Description | Location | Status | Evidence |
|---|-------|--------|-------------|----------|--------|----------|
| H-01 | ReDoS in SecurityPolicy | Security Review, Investigate, CSO | Possessive quantifiers don't prevent catastrophic backtracking; timeout checked AFTER match | `SecurityPolicy.java:17-117`, `validate():153-179` | ❌ NOT DONE | 100KB input → CPU DoS |
| H-02 | In-Memory CostTracker | Investigate, CSO | `ConcurrentHashMap` + `AtomicLong`; all LLM cost data, budgets lost on restart | `CostTracker.java` | ❌ NOT DONE | Budget reset → unlimited spend |
| H-03 | In-Memory ApprovalService | Investigate, CSO | `ConcurrentHashMap`; pending approvals lost on restart; tasks stuck in `WAITING_APPROVAL` | `ApprovalService.java` | ❌ NOT DONE | Stuck workflows after restart |
| H-04 | OOM via Unbounded JSON | Security Review, Investigate, CSO | `ObjectMapper.readValue(planJson)` no size limit | `GenerateCommand.java:189`, `PlanReviewer.java:85` | ❌ NOT DONE | 100MB JSON → OOM |
| H-05 | Command Injection in DoctorCommand | Security Review, Investigate, CSO | Windows `cmd /c` with user-controlled args; special chars (`&`,`|`) interpreted | `DoctorCommand.java:230-234` | ✅ DONE | Fixed to use `docker system info` |
| H-06 | No Docker Image Validation | Security Review, CSO, Investigate | `config.getDockerImage()` used directly without allowlist | `SandboxService.java:404`, `SandboxConfig.java:13` | ❌ NOT DONE | Malicious image → arbitrary container |
| H-07 | No Request/Response Size Limits | Security Review, CSO, Investigate | `WebClient` no max body size; `ObjectMapper` no max input size | `OrchestratorClient.java`, `GenerateCommand.java:189` | ❌ NOT DONE | Large request/response → OOM |
| H-08 | Poor Error Messages | QA, Investigate, CSO | Raw socket errors (`getsockopt`) shown to users instead of friendly guidance | `OrchestratorClient.mapError():183-199`, all commands | ✅ DONE | Friendly messages for connection errors |
| H-09 | Race Condition in Maven Cache | Investigate, CSO | TOCTOU between `listVolumes` check and `createVolume` | `SandboxService.java:423-435`, `499-505` | ❌ NOT DONE | Concurrent builds → cache corruption |
| H-10 | PlanReviewer Creates New Scanner | Investigate, CSO | `new Scanner(System.in)` inside loop instead of reusing instance field | `PlanReviewer.java:52,61` | ❌ NOT DONE | Resource leak, broken input |
| H-11 | ProgressRenderer Creates New Spinner | Investigate, CSO | `spinner = new Spinner(displayMsg)` every 200ms floods console | `ProgressRenderer.java:99-100` | ❌ NOT DONE | Console corruption, high CPU |
| H-11 | Duplicate Pattern Compilation | Investigate, CSO | Same regex compiled twice in `extractJavaVersion()` | `DoctorCommand.java:254-255` | ❌ NOT DONE | Wasteful, recompiled each call |
| H-12 | No Rate Limiting | Security Review, CSO | No bucket4j/Resilience4j rate limits; unrestricted calls | `TaskController.java`, `application.yml` | ❌ NOT DONE | Abuse/DoS possible |
| H-13 | No Structured Audit Logging | Security Review, CSO | `AuditService` logs to file only; no correlation IDs, searchable fields | `AuditService.java` | ❌ NOT DONE | Cannot trace security events |
| H-14 | NvidiaProvider NPE | Security Review, CSO | Null usage fields cause NPE in cost tracking | `NvidiaProvider.java`, `ModelRouter.java:156-184` | ❌ NOT DONE | Null check incomplete |
| H-15 | No Health Check Endpoints | Security Review, CSO | No `/actuator/health` for providers, circuit breakers, latency | `ModelRouter.java` | ❌ NOT DONE | Cannot monitor provider health |
| H-16 | No Artifact Integrity Verification | Security Review, CSO | Downloaded artifacts not verified (checksums/signatures) | `TaskCommand.Artifacts.java` | ❌ NOT DONE | Supply chain risk |
| H-17 | LLM Prompt Injection Risk | CSO | `SecurityPolicy` validates generated code, not prompts; prompts may contain secrets | `SecurityPolicy.java`, `engine-llm` | ❌ NOT DONE | Prompt injection → malicious code gen |
| H-18 | Excessive Agency in Non-Interactive | CSO | `--non-interactive` = no human-in-the-loop for code gen + execution + approval | `GenerateCommand.java:45`, `PlanReviewer.java` | ❌ NOT DONE | Autonomous destructive ops |
| H-19 | Keys Rotation Unverified | Security Review, CSO | `.env.example` exists but no verification of `.gitignore`, git history, CI secret management | `.env.example`, `.gitignore`, CI config | ❌ NOT DONE | P0.4 in TODO |

## MEDIUM Severity (Reliability / Hardening)

| # | Issue | Source | Description | Location | Status | Evidence |
|---|-------|--------|-------------|----------|--------|----------|
| M-01 | Config Command UX Confusion | QA, Investigate | `config show` doesn't work (use `list`); help doesn't explain subcommands | `ConfigCommand.java` | ❌ NOT DONE | Misleading UX |
| M-02 | Project/Task Commands Show Wrong Error | QA, Investigate | Says "not implemented on server" when real issue is server down | `ProjectCommand.List.java:35` | ✅ DONE | Misleads users |
| M-03 | Duplicate Pattern Compilation | Investigate, CSO | Same regex compiled twice in `extractJavaVersion()` | `DoctorCommand.java:254-255` | ❌ NOT DONE | Wasteful |
| M-04 | Docker Volume Cleanup Incomplete | Investigate, CSO | Only cleans "run-isolated" volumes, not "shared" volumes | `SandboxService.java:106-124` | ❌ NOT DONE | Orphaned volumes |
| M-05 | No OpenAPI Spec Generated | Security Review, CSO | API inventory missing | Controllers | ❌ NOT DONE | OWASP API9 gap |
| M-06 | No Egress Filtering on LLM Calls | Security Review, CSO | Outbound requests to providers unrestricted | `OrchestratorClient.java`, `engine-llm` | ❌ NOT DONE | ASVS v5.0.0-1.3.6 |
| M-07 | Fail-Open on Docker Failure | Security Review, CSO | Sandbox continues on Docker connection failure | `SandboxService.java` | ❌ NOT DONE | ASVS v5.0.0-16.5.3 |
| M-08 | SecurityPolicy Continues on Regex Timeout | Security Review, CSO | Doesn't fail validation when timeout hits | `SecurityPolicy.java:159-166` | ❌ NOT DONE | Bypass possible |
| M-09 | Architecture Doc vs Reality Gap | Design Review | Plan claims "CLI talks to REST server" but server disabled in build | `ORCHESTRATOR_PLAN.md` vs `pom.xml` | ✅ DONE | Fixed by module split |
| M-10 | Misleading Test Claims | Design Review | "99 tests passing" but 0 integration tests pass (require server) | `ORCHESTRATOR_PLAN.md:27` | 🔄 IN PROGRESS | Fixed architecture enables tests |
| M-11 | Unverifiable Sandbox Claims | Design Review | "Contains malicious code" but no logs to verify | `ORCHESTRATOR_PLAN.md:175` | ✅ DONE | Container logs now work |
| M-12 | False Portfolio Claim | Design Review | "Stranger can install and run" but server won't start | `ORCHESTRATOR_PLAN.md:185` | ✅ DONE | Fixed by module split |
| M-13 | No Design Doc (DESIGN.md) | Design Review | Missing design system documentation | Project root | ❌ NOT DONE | Design review gap |

## LOW Severity (Code Quality / Technical Debt)

| # | Issue | Source | Description | Location | Status | Evidence |
|---|-------|--------|-------------|----------|--------|----------|
| L-01 | Architecture Doc vs Reality Gap | Design Review | Plan claims "CLI talks to REST server" but server disabled in build | `ORCHESTRATOR_PLAN.md` vs `pom.xml` | ✅ DONE | Fixed by module split |
| L-02 | Misleading Test Claims | Design Review | "99 tests passing" but 0 integration tests pass (require server) | `ORCHESTRATOR_PLAN.md:27` | 🔄 IN PROGRESS | Fixed architecture enables tests |
| L-03 | Unverifiable Sandbox Claims | Design Review | "Contains malicious code" but no logs to verify | `ORCHESTRATOR_PLAN.md:175` | ✅ DONE | Container logs now work |
| L-04 | False Portfolio Claim | Design Review | "Stranger can install and run" but server won't start | `ORCHESTRATOR_PLAN.md:185` | ✅ DONE | Fixed by module split |
| L-05 | No Design Doc (DESIGN.md) | Design Review | Missing design system documentation | Project root | ❌ NOT DONE | Design review gap |
| L-06 | No OpenAPI Spec Generated | CSO | API inventory missing | Controllers | ❌ NOT DONE | OWASP API9 gap |
| L-07 | No Egress Filtering on LLM Calls | CSO | Outbound requests to providers unrestricted | `OrchestratorClient.java`, `engine-llm` | ❌ NOT DONE | ASVS v5.0.0-1.3.6 |
| L-08 | Fail-Open on Docker Failure | CSO | Sandbox continues on Docker connection failure | `SandboxService.java` | ❌ NOT DONE | ASVS v5.0.0-16.5.3 |
| L-09 | SecurityPolicy Continues on Regex Timeout | CSO | Doesn't fail validation when timeout hits | `SecurityPolicy.java:159-166` | ❌ NOT DONE | Bypass possible |
| L-10 | Deprecation Warnings in JwtAuthenticationFilter | QA, Compile | `setSigningKey`, `parseClaimsJws`, `getBody` deprecated in jjwt 0.12.x | `JwtAuthenticationFilter.java:77-93` | ⚠️ WONT FIX | Non-breaking, API still works |
| L-11 | Unchecked Conversion Warnings | QA, Compile | `claims.get("roles", List.class)` raw type | `JwtAuthenticationFilter.java:96, 141` | ⚠️ WONT FIX | Type-safe at runtime |
| L-12 | Deprecated API Usage in ConfigManager | QA, Compile | Uses deprecated Spring API | `ConfigManager.java` | ⚠️ WONT FIX | Non-breaking |

---

## Summary by Source

| Review Source | Critical | High | Medium | Low | Total | Done | In Progress | Not Done | Won't Fix |
|---------------|----------|------|--------|-----|-------|------|-------------|----------|----------|
| **QA** | 3 | 2 | 2 | 3 | 10 | 6 | 0 | 2 | 2 |
| **Security Review** | 2 | 8 | 2 | 2 | 14 | 5 | 0 | 7 | 2 |
| **Design Review** | 0 | 0 | 2 | 12 | 14 | 5 | 1 | 9 | 0 |
| **Investigate** | 3 | 9 | 5 | 0 | 17 | 7 | 1 | 9 | 0 |
| **CSO** | 4 | 10 | 4 | 3 | 21 | 7 | 1 | 11 | 2 |
| **TOTAL (Unique)** | **6** | **19** | **10** | **18** | **67** | **22** | **2** | **37** | **6** |

---

## Completion Rate

| Severity | Total | Done | Rate |
|----------|-------|------|------|
| **Critical** | 6 | 4 | 67% |
| **High** | 19 | 4 | 21% |
| **Medium** | 10 | 2 | 20% |
| **Low** | 18 | 6 | 33% |
| **Warnings** | 6 | 0 | 0% (Won't Fix) |
| **TOTAL** | **67** | **22** | **33%** |

---

## Blockers for Release

| Blocker | Must Fix Before Any Release |
|---------|----------------------------|
| **CR-01** | Server architecture - tool literally cannot work |
| **CR-02** | No offline mode - `--dry-run` fails without server |
| **CR-03** | Container logs - cannot debug failures |
| **CR-04** | Auth bypass - approval gate is decorative |
| **CR-05** | No TLS - credentials in plaintext |
| **CR-06** | Docker socket - host escape path |

---

## Recommended Next Steps (Priority Order)

1. **CR-05: TLS** - Add SSL config to WebClient and server (~4 hours)
2. **H-01: ReDoS** - Atomic groups + thread-based timeout in SecurityPolicy (~1 day)
3. **H-04: JSON Size Limits** - Add 1MB limit to ObjectMapper (~2 hours)
4. **H-07: Request/Response Size Limits** - Configure WebClient limits (~2 hours)
4. **H-06: Docker Image Validation** - Allowlist in SandboxConfig + validation (~4 hours)
5. **CR-05: TLS** - SSL config for WebClient + self-signed cert for dev (~4 hours)
6. **H-02/H-03: Persistence** - SQLite persistence for CostTracker + ApprovalService (~2 weeks)
7. **H-09: Race Condition** - Idempotent volume creation (~2 hours)
8. **CR-02 Full: Offline Mode** - Complete local template rendering (~1 day)

---

**Last Updated:** 2026-10-30  
**Next Review:** After Phase 1 fixes (TLS, ReDoS, JSON limits)