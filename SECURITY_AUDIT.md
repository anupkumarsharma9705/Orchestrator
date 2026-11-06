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
| Dependencies (docs) | `npm audit` | `docs/package-lock.json` | 25 remaining, all Docusaurus build/dev toolchain |
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
| H-1 | **Arbitrary Maven goals execute plugins on the server host.** `runMavenBuild` and `runMavenTest` passed caller/model-supplied goals straight to `ProcessBuilder`. `exec:exec` or an arbitrary plugin coordinate gives code execution. `runMavenTest` is additionally classified `SAFE`. | `RunMavenBuildTool.java:86`, `RunMavenTestTool.java:78` | **Fixed** — goals restricted to lifecycle phases and read-only reporting goals via `MavenGoalValidator`. Residual risk: a build still executes the project's own plugins/tests on the host. |
| H-2 | **Untrusted project root path.** Task API persists a caller-supplied `rootPath`; all tools then operate on it. Combined with host tool execution this widens the blast radius. | `TaskController`, `ProjectManager` | **Open** — restrict to configured workspace roots and canonicalize/symlink-check. |
| H-3 | **Approval identity is caller-supplied and auto-satisfied.** `ExecutionEngine` and `RepairLoopService` always build a permission context with `userId("system")`, `actorType("ai")`, `hasApproval(true)`. `@PreAuthorize` is inert because `@EnableMethodSecurity` is absent. | `ExecutionEngine.java:227`, `RepairLoopService.java:329`, `SecurityConfig.java` | **Open** — derive approval from an authenticated approver and enable method security with real authorities. |
| H-4 | **Jackson 2.21.5 is vulnerable.** DoS and deserialization advisories: CVE-2026-89425, CVE-2026-89407, CVE-2026-91777, CVE-2026-83557, CVE-2026-68497, CVE-2026-19032, CVE-2026-91776. | OSV; server dependency tree | **Fixed** — Jackson BOM 2.21.7 (server: `jackson-core`/`databind` 2.21.7, annotations 2.21). |
| H-5 | **OpenTelemetry 1.41.0 with mixed transitive versions.** CVE-2026-118259 (quadratic tracestate parsing) fixed in 1.67.0; CVE-2026-45292 (unbounded baggage allocation) fixed in 1.62.0. The previous tree mixed 1.41.0 and 1.62.0. | OSV; server dependency tree | **Fixed** — OTel BOM 1.67.0; all core artifacts aligned. Zipkin exporter pinned to 1.64.0, its last published release. |
| H-6 | **Test-scope AssertJ 3.24.2** (CVE-2026-24400, XXE in `isXmlEqualTo`). | OSV; root POM | **Fixed** — 3.27.7. Test scope only; not shipped in the server runtime. |

### 2.2 Medium

| ID | Finding | Evidence | Status |
|---|---|---|---|
| M-1 | **Argument injection into Trivy.** `dockerImageScan` passed `params.image` unvalidated; `trivyScan` allowed a leading `-`. A value such as `--output=...` is interpreted as a Trivy flag (no shell involved, so this is option injection rather than shell injection). | `DockerImageScanTool.java:75`, `TrivyScanTool.java:87` | **Fixed** — anchored image-reference validation; tags, registry ports and `@sha256` digests remain supported. |
| M-2 | **Dockerfile path traversal.** `dockerfile` allowed `../`, so an arbitrary host file could be used as the build file. Build args and tags were otherwise validated. | `DockerBuildTool.java:88` | **Fixed** — resolved Dockerfile must remain under the project path. |
| M-3 | **Rate-limit bypass via spoofed `X-Forwarded-For` / `X-Real-IP`.** The API-key filter trusts these headers unconditionally, so failed-attempt throttling can be evaded. | `SecurityConfig.java` (`getClientIp`) | **Open** — only honor forwarding headers from configured trusted proxies; otherwise use `getRemoteAddr()`. |
| M-4 | **Self-update skips checksum verification silently.** | `SelfUpdateCommand.java` | **Open** — fail closed when no checksum is available or verification cannot be performed. |
| M-5 | **Absolute artifact paths disclosed** to API clients. | `TaskController.java` | **Open** — return workspace-relative paths. |
| M-6 | **Mutable GitHub Action tags** (14 occurrences) allow a compromised tag to change code executed in CI. | Semgrep; `.github/workflows`, template `ci.yml` | **Fixed** — all actions pinned to commit SHAs. |
| M-7 | **`SnakeYAML` used via default constructor.** Version 2.3 already rejects global tags, but the code did not state its safety posture. | `ConventionLoader.java:17` | **Fixed** — explicit `SafeConstructor` with `LoaderOptions`. |
| M-8 | **Docs toolchain vulnerabilities.** Docusaurus/webpack chain: `tinypool` (critical, prototype-pollution RCE gadget), `serialize-javascript` (high RCE/XSS/DoS), `uuid` (moderate), `deepmerge` (low). The published site is static, so exposure is limited to build/dev-server environments. | `npm audit` | **Partially fixed** — direct `prismjs` bumped to 1.30.0 (CVE-2024-53382). The remaining 25 advisories require a patched Docusaurus release; do not force `uuid`/`tinypool` major overrides across Docusaurus internals. |

### 2.3 Low / informational

| ID | Finding | Disposition |
|---|---|---|
| L-1 | Docs routes were outside the security filter chains (`/api/**`, `/actuator/**` only). | Open; intentional for static docs, but confirm no sensitive route is added outside `/api`. |
| L-2 | Unencrypted `Socket` in `CertificatePinningManager.generatePinForHost` and the localhost port probe in `DoctorCommand`. | False positive / inert: the pinning method throws `UnsupportedOperationException`; the other socket is a localhost availability probe. Consider deleting the dead method. |
| L-3 | Semgrep secret hits: AWS key literal and PGP block are detector patterns in `SecretScanner`; the template `JWT_SECRET` is an explicitly test-only placeholder. | No credential leak. Gitleaks independently found 0 leaks. |
| L-4 | `ProcessBuilder` hits in `DependencyCheckTool` and `DoctorCommand`. | No injection: fixed argument lists, confined paths, no shell. |
| L-5 | No tracked `LICENSE`; README says MIT while manifests/config say Apache-2.0. | Open; resolve before publishing. |

## 3. Remediation applied in this pass

- `MavenGoalValidator` plus call-site checks in `runMavenBuild` / `runMavenTest`.
- Anchored image-reference validation for `dockerImageScan` and `trivyScan`.
- Dockerfile containment check in `dockerBuild`.
- Explicit `SafeConstructor` in `ConventionLoader`.
- Jackson 2.21.7, OpenTelemetry 1.67.0 (Zipkin exporter 1.64.0), AssertJ 3.27.7.
- All GitHub Actions pinned to commit SHAs in both workflows and the generated-project template.
- `prismjs` 1.30.0 in the docs site.
- New unit tests: `MavenGoalValidatorTest`, `ContainerReferenceValidationTest`.

Verification after remediation:

- `mvn -B -ntp clean verify` — **BUILD SUCCESS** (all modules, 32 CLI integration tests included).
- Targeted tests — 6/6 passed.
- `npm run build` in `docs/` — **SUCCESS**.
- Server dependency tree — Jackson 2.21.7, OTel 1.67.0, Zipkin 1.64.0.

## 4. Remaining production-readiness blockers

1. **Sandboxing.** Server tools (`mvn`, scanners, Docker) still execute on the host. Building a project executes its plugins/tests. The sandbox image `orchestrator/sandbox:1.0.0` is not built or published.
2. **Authentication/authorization.** One shared API key, no RBAC, caller-supplied approval identity, method security not enabled.
3. **Workspace confinement.** `rootPath` and artifact paths are not constrained or sanitized.
4. **Deployment validation.** Docker Compose stack, mTLS stack, sandbox image, and a dockerized generated app remain untested. No live model-provider key has exercised online generation.
5. **Supply chain.** No root dependency scan in CI, no SBOM, no artifact signing, no Maven Central publication, installers unpublished.
6. **Data durability.** In-memory cost tracking; no backup/retention automation; no queue/worker split.
7. **Release hygiene.** Version mismatch (`0.0.1-SNAPSHOT` vs docs `0.1.0`), stale status documents (`TESTING.md`, `ALL_ISSUES_SUMMARY.md`, `CEO_REVIEW.md`, `orchestrator-server/TODO.md`), license inconsistency.
8. **Credential rotation.** Rotate the OpenRouter key that appeared in the deleted `test-results/compile.log`.

## 5. Re-running the audit

To obtain a comprehensive (runtime-reproducing) CSO attestation, run the official helper on Linux with Docker available at a trusted `unix:///` endpoint:

```bash
gstack-cso doctor --repo /path/to/orchestrator
gstack-cso start --repo /path/to/orchestrator --comprehensive
```

Do not treat this report's Docker scanner results as a substitute for that run.
