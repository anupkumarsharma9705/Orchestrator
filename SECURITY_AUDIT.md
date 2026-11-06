# Security Audit — Java Companion Orchestrator

- **Target:** `D:\ANUP\WASTE\orchestrator\orchestrator`
- **Baseline commit:** `1dbe52b Harden orchestrator for early-access release` (plus the remediation changes described below)
- **Audit dates:** 2026-11-05 to 2026-11-06
- **Official CSO run:** `1793846202085-fc2e19d4905f0934` — finished as **partial** (static-only)
- **Supplementary scanners:** Gitleaks, Semgrep, OSV-Scanner (Docker) — **outside the CSO contract**

## 1. Scope and coverage

| Layer | Tool | Coverage | Result |
|---|---|---|---|
| Official CSO | `gstack-cso` 3.0.0 | Static snapshot only | Partial; 6 findings submitted |
| Secrets | Gitleaks container | 2 commits, ~3.6 MB | 0 leaks |
| SAST | Semgrep container (`p/default`, `p/secrets`) | 455 git-tracked files | 29 results, classified below |
| Dependencies (Java) | OSV-Scanner container | Maven POMs | Partial: reactor modules and template placeholder could not resolve |
| Dependencies (docs) | `npm audit` | `docs/package-lock.json` | 5 remaining, all low `deepmerge` advisories via `@svgr` |
| Container/filesystem | Trivy container | Not completed | Maven Central HTTP 429; Trivy also fatals on `pom.xml` |

### Why the CSO run is partial

`gstack-cso`'s `windows-docker.ts` always throws for a native Windows Docker transport, even when Docker Desktop is healthy, and additionally refuses Docker installed outside `Program Files` (this host installs per-user under `%LOCALAPPDATA%\Programs\DockerDesktop`). A Linux `gstack-cso` binary is not installed. Comprehensive runtime reproduction therefore requires a Linux host. The Docker scans in this report were run directly and are **supplementary evidence**, not part of the CSO attestation.

### Scanner caveats

- OSV-Scanner filtered 58 local/unscannable packages and could not resolve `com.example:*:0.0.1-SNAPSHOT` reactor modules or the template's `{{spring.boot.version}}` placeholder. Dependency coverage is therefore incomplete.
- Trivy `vuln` scans failed because `repo.maven.apache.org` returned `429 Too Many Requests` (`Retry-After: 1800`) while resolving `spring-data-bom-2026.0.1.pom`. Trivy also fatals on `pom.xml` during `secret,misconfig` scans. No Trivy evidence is claimed.
- Semgrep scanned only git-tracked files and skipped 54 files via `.semgrepignore`.

## 2. Findings and disposition

### 2.1 High

| ID | Finding | Evidence | Status |
|---|---|---|---|
| H-1 | **Arbitrary Maven goals execute plugins on the server host.** `runMavenBuild` and `runMavenTest` passed caller/model-supplied goals straight to `ProcessBuilder`. `exec:exec` or an arbitrary plugin coordinate gives code execution. `runMavenTest` is additionally classified `SAFE`. | `RunMavenBuildTool.java:86`, `RunMavenTestTool.java:78` | **Fixed** — goals restricted to lifecycle phases and read-only reporting goals via `MavenGoalValidator`; Maven, test, Git, Semgrep, Trivy, Dependency-Check, Docker build and image-scan tools now execute through the Docker `SandboxService` with argv-only commands, so no target command runs on the server host. The sandbox fails closed when Docker is unreachable. |
| H-2 | **Untrusted project root path.** Task API persists a caller-supplied `rootPath`; all tools then operate on it. Combined with host tool execution this widens the blast radius. | `TaskController`, `ProjectManager` | **Fixed** — `WorkspaceProperties` requires an absolute, canonical (`toRealPath`) directory below `orchestrator.workspace.allowed-roots`; symlink escapes are rejected; project IDs, names and build tools are validated. The sandbox applies the same allow-list to mounted workspaces, and API responses return workspace-relative paths. |
| H-3 | **Approval identity is caller-supplied and auto-satisfied.** `ExecutionEngine` and `RepairLoopService` always build a permission context with `userId("system")`, `actorType("ai")`, `hasApproval(true)`. `@PreAuthorize` is inert because `@EnableMethodSecurity` is absent. | `ExecutionEngine.java:227`, `RepairLoopService.java:329`, `SecurityConfig.java` | **Fixed** — `@EnableMethodSecurity` is enabled; the bootstrap key carries `USER`/`APPROVER`/`ADMIN`, role-bearing keys are supported, and `@PreAuthorize` gates task, approval and admin routes. Requesters and approvers are derived from the authenticated `Authentication`, never from the request body. The execution path already blocks on a real `ApprovalService` decision before running the plan. Residual: internal `PermissionContext` still uses a synthetic `system` actor; no shipped tool declares `APPROVAL_REQUIRED` or `HUMAN_ONLY`. |
| H-4 | **Jackson 2.21.5 is vulnerable.** DoS and deserialization advisories: CVE-2026-89425, CVE-2026-89407, CVE-2026-91777, CVE-2026-83557, CVE-2026-68497, CVE-2026-19032, CVE-2026-91776. | OSV; server dependency tree | **Fixed** — Jackson BOM 2.21.7 (server: `jackson-core`/`databind` 2.21.7, annotations 2.21). |
| H-5 | **OpenTelemetry 1.41.0 with mixed transitive versions.** CVE-2026-118259 (quadratic tracestate parsing) fixed in 1.67.0; CVE-2026-45292 (unbounded baggage allocation) fixed in 1.62.0. The previous tree mixed 1.41.0 and 1.62.0. | OSV; server dependency tree | **Fixed** — OTel BOM 1.67.0; all core artifacts aligned. Zipkin exporter pinned to 1.64.0, its last published release. |
| H-6 | **Test-scope AssertJ 3.24.2** (CVE-2026-24400, XXE in `isXmlEqualTo`). | OSV; root POM | **Fixed** — 3.27.7. Test scope only; not shipped in the server runtime. |

### 2.2 Medium

| ID | Finding | Evidence | Status |
|---|---|---|---|
| M-1 | **Argument injection into Trivy.** `dockerImageScan` passed `params.image` unvalidated; `trivyScan` allowed a leading `-`. A value such as `--output=...` is interpreted as a Trivy flag (no shell involved, so this is option injection rather than shell injection). | `DockerImageScanTool.java:75`, `TrivyScanTool.java:87` | **Fixed** — anchored image-reference validation; tags, registry ports and `@sha256` digests remain supported. |
| M-2 | **Dockerfile path traversal.** `dockerfile` allowed `../`, so an arbitrary host file could be used as the build file. Build args and tags were otherwise validated. | `DockerBuildTool.java:88` | **Fixed** — resolved Dockerfile must remain under the project path. |
| M-3 | **Rate-limit bypass via spoofed `X-Forwarded-For` / `X-Real-IP`.** The API-key filter trusts these headers unconditionally, so failed-attempt throttling can be evaded. | `SecurityConfig.java` (`getClientIp`) | **Fixed** — forwarding headers are ignored unless `orchestrator.security.trust-forwarded-headers=true`; when enabled, the right-most `X-Forwarded-For` hop is used and IP values are length-capped. Otherwise `getRemoteAddr()` is authoritative. |
| M-4 | **Self-update skips checksum verification silently.** | `SelfUpdateCommand.java` | **Fixed** — a published SHA-256 is mandatory. The command resolves `<jar>.sha256`, `checksums.txt` or `SHA256SUMS`, rejects malformed values, verifies the download, and exits non-zero without installing when no checksum exists or the digest mismatches. |
| M-5 | **Absolute artifact paths disclosed** to API clients. | `TaskController.java`, `ArtifactStore.java` | **Fixed** — artifact records store workspace-relative paths, `ArtifactStore` rejects traversal and sanitizes names, project responses use `WorkspaceProperties.toRelativeDisplayPath`, and downloads set a sanitized `Content-Disposition` plus `X-Content-Type-Options: nosniff`. |
| M-6 | **Mutable GitHub Action tags** (14 occurrences) allow a compromised tag to change code executed in CI. | Semgrep; `.github/workflows`, template `ci.yml` | **Fixed** — all actions pinned to commit SHAs. |
| M-7 | **`SnakeYAML` used via default constructor.** Version 2.3 already rejects global tags, but the code did not state its safety posture. | `ConventionLoader.java:17` | **Fixed** — explicit `SafeConstructor` with `LoaderOptions`. |
| M-8 | **Docs toolchain vulnerabilities.** Docusaurus/webpack chain: `tinypool` (critical, prototype-pollution RCE gadget), `serialize-javascript` (high RCE/XSS/DoS), `uuid` (moderate), `deepmerge` (low). The published site is static, so exposure is limited to build/dev-server environments. | `npm audit` | **Fixed (residual accepted)** — npm `overrides` pin `serialize-javascript` 7.1.4, `tinypool` 2.2.0 and `uuid` 11.1.1 (CJS-compatible patched release). `npm audit` is down from 25 advisories (15 critical) to **5 low**, all `deepmerge` via `@svgr`; no fixed `deepmerge` release exists. |

### 2.3 Low / informational

| ID | Finding | Disposition |
|---|---|---|
| L-1 | Docs routes were outside the security filter chains (`/api/**`, `/actuator/**` only). | **Fixed** — a catch-all filter chain authenticates every path except the explicit public set (`/api/version`, health, info). Static docs, if served, therefore require authentication like any other non-public route. |
| L-2 | Unencrypted `Socket` in `CertificatePinningManager.generatePinForHost` and the localhost port probe in `DoctorCommand`. | False positive / inert: the pinning method throws `UnsupportedOperationException`; the other socket is a localhost availability probe. Consider deleting the dead method. |
| L-3 | Semgrep secret hits: AWS key literal and PGP block are detector patterns in `SecretScanner`; the template `JWT_SECRET` is an explicitly test-only placeholder. | No credential leak. Gitleaks independently found 0 leaks. |
| L-4 | `ProcessBuilder` hits. | **Fixed for server tools** — `DependencyCheckTool`, Git tools and all Maven/scanner/Docker tools now use the sandbox. Residual: `DoctorCommand` (local diagnostics) and `engine-core` `Coder.compileAndVerify` (library code not invoked by the server execution path) still use host `ProcessBuilder` with fixed arguments. |
| L-5 | No tracked `LICENSE`; README says MIT while manifests/config say Apache-2.0. | Open; resolve before publishing. |

## 3. Remediation applied in this pass

- `MavenGoalValidator` plus call-site checks in `runMavenBuild` / `runMavenTest`.
- Anchored image-reference validation for `dockerImageScan` and `trivyScan`.
- Dockerfile containment check in `dockerBuild`.
- All Maven, test, Git, Semgrep, Trivy, Dependency-Check, Docker build and image-scan tools routed through the Docker `SandboxService` using argv-only commands; image scans export the image to a tar instead of mounting the Docker socket. Sandbox fails closed when the daemon is unavailable.
- `WorkspaceProperties` canonical allow-list for project roots, enforced by `ProjectManager` and shared with the sandbox `workspace-roots`.
- `SecurityProperties` + rewritten `SecurityConfig`: `@EnableMethodSecurity`, bootstrap and role-bearing keys, constant-time comparison, principal-derived identity, trusted-proxy-only forwarding headers, catch-all authenticated filter chain.
- `TaskController`: requester/approver from `Authentication`; `ApprovalService.isAwaitingDecision`; relative paths; safe downloads; duplicate approval controller removed.
- `ArtifactStore`: relative stored paths, traversal containment, sanitized task/artifact names.
- `SelfUpdateCommand`: checksum mandatory, sidecar resolution, fail closed on missing/malformed/mismatched digests.
- Explicit `SafeConstructor` in `ConventionLoader`.
- Jackson 2.21.7, OpenTelemetry 1.67.0 (Zipkin exporter 1.64.0), AssertJ 3.27.7.
- All GitHub Actions pinned to commit SHAs in both workflows and the generated-project template.
- Docs npm `overrides` for `serialize-javascript` 7.1.4, `tinypool` 2.2.0 and `uuid` 11.1.1.
- New/updated unit tests: `MavenGoalValidatorTest`, `ContainerReferenceValidationTest`, `ProjectManagerTest`, `SandboxServiceTest`, `SelfUpdateCommandTest`.

Verification after remediation:

- `mvn -B -ntp clean verify` — **BUILD SUCCESS** across all 12 modules; server 62 tests (1 skipped), CLI 35 tests, `engine-sandbox` 32 tests.
- Targeted server tests (ProjectManager, container validation, tool tests) — passed.
- `engine-sandbox` tests — 32 passed.
- Spring context tests (`FreshOrchestratorApplicationTests`, `JpaRepositoriesContextTest`, `ApiKeyRequirementsTest`) — passed.
- `npm audit` in `docs/` — 5 low advisories (all `deepmerge`).
- `npm run build` in `docs/` — **SUCCESS**.
- Server dependency tree — Jackson 2.21.7, OTel 1.67.0, Zipkin 1.64.0.

## 4. Remaining production-readiness blockers

1. **Sandbox image publication.** Build and publish `orchestrator/sandbox:1.0.0`; without it, sandboxed commands fail closed. The Docker engine pipe was unavailable on this host, so container-level verification is still outstanding.
2. **Library-level host execution.** `engine-core` `Coder.compileAndVerify` still shells out to host Maven. The server execution path does not invoke `Coder`, but consumers of the engine library should sandbox it before use.
3. **Deployment validation.** Docker Compose stack, mTLS stack, sandbox image, and a dockerized generated app remain untested. No live model-provider key has exercised online generation.
4. **Supply chain.** No root dependency scan in CI, no SBOM, no artifact signing, no Maven Central publication, installers unpublished.
5. **Data durability.** In-memory cost tracking; no backup/retention automation; no queue/worker split.
6. **Release hygiene.** Version mismatch (`0.0.1-SNAPSHOT` vs docs `0.1.0`), stale status documents (`TESTING.md`, `ALL_ISSUES_SUMMARY.md`, `CEO_REVIEW.md`, `orchestrator-server/TODO.md`), license inconsistency.
7. **Credential rotation.** Rotate the OpenRouter key that appeared in `orchestrator-server/.idea/workspace.xml`. The value has been scrubbed from the working tree and is not in git history, but the exposed credential must still be revoked and reissued at the provider. A repository-wide scan now finds no `sk-or-v1-...` key.

## 5. Re-running the audit

To obtain a comprehensive (runtime-reproducing) CSO attestation, run the official helper on Linux with Docker available at a trusted `unix:///` endpoint:

```bash
gstack-cso doctor --repo /path/to/orchestrator
gstack-cso start --repo /path/to/orchestrator --comprehensive
```

Do not treat this report's Docker scanner results as a substitute for that run.
