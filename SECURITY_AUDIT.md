# Security Audit — Java Companion Orchestrator

- **Target:** `D:\ANUP\WASTE\orchestrator\orchestrator`
- **Baseline commit:** `1dbe52b Harden orchestrator for early-access release` (plus the remediation changes described below)
- **Audit dates:** 2026-11-05 to 2026-11-06
- **Remediation passes:** 2026-11-07 (SEC-07 to SEC-13), 2026-11-07 (SEC-14 to SEC-18)
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
| M-3 | **Rate-limit bypass via spoofed `X-Forwarded-For` / `X-Real-IP`.** The API-key filter trusts these headers unconditionally, so failed-attempt throttling can be evaded. | `SecurityConfig.java` (`getClientIp`) | **Fixed** — forwarding headers are ignored unless `orchestrator.security.trust-forwarded-headers=true`; when enabled, the right-most `X-Forwarded-For` hop is used and IP values are length-capped. Otherwise `getRemoteAddr()` is authoritative. `SecurityConfig` and `RateLimitConfig` now share `ClientIpResolver`, so API-key throttling and endpoint rate limits key on the same identity; covered by `ClientIpResolverTest`. |
| M-4 | **Self-update skips checksum verification silently.** | `SelfUpdateCommand.java` | **Fixed** — a published SHA-256 is mandatory. The command resolves `<jar>.sha256`, `checksums.txt` or `SHA256SUMS`, rejects malformed values, verifies the download, and exits non-zero without installing when no checksum exists or the digest mismatches. |
| M-5 | **Absolute artifact paths disclosed** to API clients. | `TaskController.java`, `ArtifactStore.java` | **Fixed** — artifact records store workspace-relative paths, `ArtifactStore` rejects traversal and sanitizes names, project responses use `WorkspaceProperties.toRelativeDisplayPath`, and downloads set a sanitized `Content-Disposition` plus `X-Content-Type-Options: nosniff`. |
| M-6 | **Mutable GitHub Action tags** (14 occurrences) allow a compromised tag to change code executed in CI. | Semgrep; `.github/workflows`, template `ci.yml` | **Fixed** — all actions pinned to commit SHAs. |
| M-7 | **`SnakeYAML` used via default constructor.** Version 2.3 already rejects global tags, but the code did not state its safety posture. | `ConventionLoader.java:17` | **Fixed** — explicit `SafeConstructor` with `LoaderOptions`. |
| M-8 | **Docs toolchain vulnerabilities.** Docusaurus/webpack chain: `tinypool` (critical, prototype-pollution RCE gadget), `serialize-javascript` (high RCE/XSS/DoS), `uuid` (moderate), `deepmerge` (low). The published site is static, so exposure is limited to build/dev-server environments. | `npm audit` | **Fixed (residual accepted)** — npm `overrides` pin `serialize-javascript` 7.1.4, `tinypool` 2.2.0 and `uuid` 11.1.1 (CJS-compatible patched release). `npm audit` is down from 25 advisories (15 critical) to **5 low**, all `deepmerge` via `@svgr`; no fixed `deepmerge` release exists. |

### 2.3 Low / informational

| ID | Finding | Disposition |
|---|---|---|
| L-1 | Docs routes were outside the security filter chains (`/api/**`, `/actuator/**` only). | **Fixed** — a catch-all filter chain authenticates every path except the explicit public set (`/api/version`, health, info). Static docs, if served, therefore require authentication like any other non-public route. |
| L-2 | Unencrypted `Socket` in `CertificatePinningManager.generatePinForHost` and the localhost port probe in `DoctorCommand`. | **Fixed** — the dead `generatePinForHost` method (which always threw) and its unused imports were removed. The remaining socket is a loopback-only availability probe with a bounded timeout; it carries no credentials. |
| L-3 | Semgrep secret hits: AWS key literal and PGP block are detector patterns in `SecretScanner`; the template `JWT_SECRET` is an explicitly test-only placeholder. | No credential leak. Gitleaks independently found 0 leaks. |
| L-4 | `ProcessBuilder` hits. | **Fixed for server tools** — `DependencyCheckTool`, Git tools and all Maven/scanner/Docker tools now use the sandbox. Residual: `DoctorCommand` (local diagnostics) and `engine-core` `Coder.compileAndVerify` (library code not invoked by the server execution path) still use host `ProcessBuilder` with fixed arguments. |
| L-5 | No tracked `LICENSE`; README says MIT while manifests/config say Apache-2.0. | **Fixed** — a tracked Apache-2.0 `LICENSE` was added, README now points to it, and the root POM, server POM, docs `package.json`, and lockfile declare Apache-2.0 consistently. |

### 2.4 Hardening pass SEC-07 to SEC-13

These items were requested as a follow-up to the initial audit. They are recorded
separately so the original H/M/L disposition remains stable.

| ID | Issue | Remediation | Verification |
|---|---|---|---|
| SEC-07 | `X-Client-Cert-DN` was accepted as a client identity whenever no certificate was present, allowing identity spoofing. | `mtls.trust-proxy-header` defaults to `false`; the header is ignored and the request is rejected with `401` unless a deployment explicitly enables it. Trusted header values must contain `=`, be at most 1024 characters, and contain no CR/LF. `MTLS_TRUST_PROXY_HEADER` is exposed in `application.yml`. | `MtlsConfigTest` — default rejection, explicit opt-in, malformed header rejection, disabled pass-through. |
| SEC-08 | Self-update had no transport allow-list or custom trust-store support, and redirects were followed without re-checking the destination. | Update traffic is HTTPS-only and restricted to `api.github.com`, `github.com`, and `*.githubusercontent.com`; redirect targets are re-validated; `owner/repo` is validated before it is interpolated into a URL. `--trust-store` / `--trust-store-type` load a PKCS12/JKS store whose password comes from `companion.update.trust-store-password` or `COMPANION_UPDATE_TRUST_STORE_PASSWORD`. The JVM default trust store is used otherwise; there is no trust-all path. | `SelfUpdateCommandTest` — scheme/host rejection, trusted hosts, repository coordinates, and trust-store password requirement. |
| SEC-09 | Rate limiting and failed-auth throttling trusted the left-most (client-supplied) `X-Forwarded-For` hop. | A shared `ClientIpResolver` ignores forwarding headers by default. When trust is enabled it uses the right-most non-empty hop (the value appended by the nearest proxy), then `X-Real-IP`, then `getRemoteAddr()`; values are capped at 64 characters. Both security layers use the same resolver. | `ClientIpResolverTest`; `FilterRegistrationTest` updated for constructor injection. |
| SEC-10 | Secret-scanner ReDoS protection measured elapsed time *after* `Matcher.find()` returned, so a catastrophic pattern could hang the thread indefinitely. | `TimeBoundedCharSequence` checks a wall-clock deadline every 256 character accesses inside the regex engine and throws `RegexTimeoutException`; `SecretScanTool` wraps each line/pattern match with the configured budget and logs the timed-out pattern without failing the scan. Java 21 has no `Matcher` time limit, so this is enforced at the `CharSequence` boundary. | `TimeBoundedCharSequenceTest` — normal match and a `(a+)+$` catastrophic-backtracking input aborted by the deadline. |
| SEC-11 | Detection and sanitization could disagree because they normalized input differently and `hasInjection()` did not honor the allow-list. | `PromptSanitizer` now has one `normalize()` (NFC, 100 KB hard limit) and one `findInjectionMatches()` shared by `sanitize()` and `hasInjection()`. Both paths consult `ALLOWED_PATTERNS`. | `PromptSanitizerTest` — consistency matrix, shared Unicode normalization, oversized input rejected by both paths. |
| SEC-12 | `EgressFilter` used blocking, unbounded `InetAddress.getAllByName()` on the request thread. | `EgressDnsResolver` runs lookups on a two-thread daemon pool with a 2 s default budget and caches positive results for 300 s and negative results for 30 s. A timeout fails closed (egress rejected); an unknown host preserves the previous fail-open-with-warning behavior. Timeout and cache TTL are configurable through `orchestrator.egress.dns-timeout-ms` / `ORCHESTRATOR_EGRESS_DNS_TIMEOUT_MS` and `orchestrator.egress.dns-cache-ttl-seconds` / `ORCHESTRATOR_EGRESS_DNS_CACHE_TTL_SECONDS`. | `EgressDnsResolverTest` — positive/negative caching and bounded slow lookup; `EgressFilterTest` — blocked literals, blocked resolution, allowed public host. |
| SEC-13 | No SBOM, no automated dependency scan, no artifact signing, and no provenance attestation. | CycloneDX 2.9.3 generates a schema 1.5 aggregate JSON SBOM (verified locally: 222 components, including all 12 reactor modules). CI uploads the SBOM and runs OWASP dependency-check 13.0.0 with `failBuildOnCVSS=7`; `NVD_API_KEY` is optional but recommended. The release workflow imports a GPG key, runs the `release-signing` profile, generates the SBOM, writes `companion.jar.sha256` and `SHA256SUMS`, signs and verifies the jar/SBOM/checksums/installer, attests build provenance with pinned `actions/attest-build-provenance`, and uploads the `.asc` files. | Local aggregate SBOM generation and reactor compile/tests. CI and release execution are **configured but not executed in this environment**; see the manual steps below. |

**SEC-13 manual prerequisites.** The release workflow requires repository secrets
`GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`; the dependency-scan job reads optional
`NVD_API_KEY`. GitHub Actions must be allowed to issue OIDC tokens for the
attestation step (`id-token: write`, `attestations: write`, both declared in the
workflow). No Maven Central publication is configured: there are no production
coordinates, signing credentials, or repository credentials for Central, and
publishing is not implied by the local `release-signing` profile.

### 2.5 Hardening pass SEC-14 to SEC-18

| ID | Issue | Remediation | Verification |
|---|---|---|---|
| SEC-14 | `CertificatePinningManager.generatePinForHost` always threw `UnsupportedOperationException`, so callers had no usable API. | Deleted the dead method and the imports it alone used; the working TLS pin generator remains. | CLI module compiles; no references remain. |
| SEC-15 | Windows CMD rendered box-drawing and status glyphs as `?` because the console code page cannot encode them. | New `TerminalSupport` checks `System.out.charset()` for every glyph the CLI uses and selects ASCII fallbacks when any sample is unencodable. `COMPANION_ASCII=1|true|yes` forces the fallback. Headings, tables, spinners, status markers, progress output, and command banners all use the selected glyphs; raw non-ASCII was removed from CLI main sources. | `TerminalSupportTest` — UTF-8 keeps Unicode, legacy code pages and ASCII force fallback, override wins. |
| SEC-16 | No tracked `LICENSE`; README said MIT while distribution manifests said Apache-2.0. | Added the Apache-2.0 `LICENSE`; README, root POM, server POM, docs `package.json`, and docs lockfile now all declare Apache-2.0. | Metadata grep is consistent; `LICENSE` is tracked. |
| SEC-17 | Outbound OkHttp client had no TLS configuration and could not trust a private CA. | `HttpClientConfig` builds an explicit `SSLContext` over the JVM default trust store and optionally adds a PKCS12/JKS trust store via `orchestrator.http.tls.trust-store`, `trust-store-password`, and `trust-store-type` (`ORCHESTRATOR_HTTP_TLS_TRUST_STORE*`). The custom store is additive: default CAs remain trusted, hostname verification is unchanged, and there is no trust-all mode. A missing or unreadable store fails startup instead of silently falling back. | `HttpClientConfigTest` — default client, additional store loading, and fail-closed missing store. |
| SEC-18 | Database-URL secret pattern missed `postgresql://`, `mariadb://`, `sqlserver://`, `oracle://`, `sqlite://`, `jdbc:` prefixes, `mongodb+srv://`, and password query parameters. | The pattern now covers `jdbc:` plus `postgres`/`postgresql`, `mongodb`/`mongodb+srv`, `mysql`, `mariadb`, `redis`/`rediss`, `sqlserver`, `oracle`, `sqlite`, `db2`, `h2`, `clickhouse`, `snowflake`, `redshift`, `trino`, `presto`, and `neo4j`. A second pattern catches `password=`/`pwd=` query parameters; credential-free URLs are not flagged as password-bearing. | `SecretScannerTest` — 16 cases covering the added schemes, JDBC and SRV forms, query parameters, and a credential-free control. |

**Audit identity consistency.** The audit filter previously resolved the client IP
itself from the left-most `X-Forwarded-For` hop, bypassing the trusted-proxy
rules used by rate limiting. It now injects the shared `ClientIpResolver`, so
audit records, API-key throttling, and endpoint limits attribute requests to the
same client identity. The spoofable local resolver was removed.

### 2.6 Release blockers R-01 to R-04

These items came from the release review that followed SEC-14 to SEC-18. R-01
and R-02 were fixed in the same pass; R-03 and R-04 were fixed in the following
pass.

| ID | Issue | Remediation | Verification |
|---|---|---|---|
| R-01 | Workspace tools validated paths with `normalize()` + `startsWith()`, so a symlink could escape the project root. | New `WorkspacePathGuard` resolves the nearest existing ancestor with `toRealPath()`, rejects symlink components, and every path-taking tool operates through it with `NOFOLLOW_LINKS`. Recursive walks skip symlinks, and `dockerBuild` rejects a symlinked `Dockerfile`. Applied to all 14 path-taking tools. | `WorkspacePathGuardTest`, `PathTakingToolsSymlinkTest`, `WriteFileToolTest`, `EditFileToolTest` (21 regression tests). |
| R-02 | `writeFile` failed for genuinely new package directories because its real-path check ran before `createDirectories`, and error messages leaked absolute server paths. | Validate the nearest existing ancestor, create the missing parent directories, re-check the real path before writing, and return workspace-relative paths in errors. Write and edit failures no longer include `getMessage()` with absolute paths. | `WriteFileToolTest`, `EditFileToolTest` — nested package creation, symlink rejection, relative error text. |
| R-03 | The generated project was a skeleton that claimed rate limiting, a login flow, and SEC-1 to SEC-10 compliance that it did not implement. It also shipped `postgres/postgres` database defaults and left `/api/auth/**` open. | The generated `SecurityConfig` now describes the scaffold accurately and removes the SEC-1 to SEC-10 claims, the `/api/auth/**` permit, and the `/api/public/**` permit. `DB_PASSWORD` is required with no default in `application.yml`, `docker-compose.yml`, and `TemplateEngine`; the Postgres port binds to `127.0.0.1`. The generated README has an explicit skeleton notice and accurate security/environment documentation. The generated CI uses clearly labelled ephemeral CI-only credentials. Offline CLI plans and output now say "scaffold" instead of claiming feature generation, and duplicate dry-run file entries were removed. The committed `library/` sample was regenerated from the templates. | `TemplateEngineTest` (9 tests) asserts no SEC claims, no open auth paths, required `DB_PASSWORD`, loopback-only Postgres, CI-only credentials, and README skeleton limitations. CLI integration tests assert the offline dry-run path. |
| R-04 | A fresh sandbox cannot run a Maven build/test because network mode is `none` and a cold Maven cache has no dependencies. Scanner tools that fetch remote rule sets or vulnerability databases are a separate limitation (see section 4). | `SandboxService.runMaven` now runs offline first. If the failure looks like dependency/plugin resolution, it runs a separate `dependency:go-offline` + `dependency:resolve-plugins` prefetch with `bridge` networking and a **read-only** workspace mount, then retries the original command with network disabled. The prefetch is bounded by `enable-dependency-prefetch` and `dependency-prefetch-timeout-seconds` (default 900 s), and is skipped for timeouts, OOM kills, or non-dependency failures. `createContainer` now takes network and read-only flags, and `createWorkspaceBind` supports `ro`. `runMavenBuild` and `runMavenTest` use the new flow. | `SandboxServiceMavenPrefetchTest` — cold offline failure → networked read-only prefetch → offline retry, plus a negative case proving compile failures are not retried with network. `SandboxServiceTest` covers prefetch defaults, read-only binds, prefetch arguments, and dependency-failure detection. |

**R-04 trust boundary.** Maven can load build extensions while resolving
dependencies, so the prefetch phase must treat the project as trusted. The
prefetch workspace is mounted read-only and the build/test phases remain
offline, but `enableDependencyPrefetch` is documented as suitable only for
trusted workspaces. Air-gapped deployments should disable it and pre-warm the
cache.

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
- SEC-07: mTLS proxy-header trust is opt-in and validated.
- SEC-08: HTTPS-only, host-allow-listed self-update with optional custom trust store.
- SEC-09: shared `ClientIpResolver` with right-most-hop semantics for security and rate limiting.
- SEC-10: hard regex deadline enforced during matching by `TimeBoundedCharSequence`.
- SEC-11: shared prompt normalization and match detection for sanitization and detection.
- SEC-12: timeout/cache-backed `EgressDnsResolver`; DNS timeout fails closed.
- SEC-13: CycloneDX SBOM, OWASP dependency-check CI job, GPG-signed release assets, and provenance attestation.
- SEC-14: removed the always-throwing `generatePinForHost` method.
- SEC-15: `TerminalSupport` selects Unicode or ASCII glyphs from the actual stdout charset, with `COMPANION_ASCII` as an explicit override.
- SEC-16: added the Apache-2.0 `LICENSE` and aligned README, POM, and docs npm metadata.
- SEC-17: outbound OkHttp TLS with JVM default trust plus an optional additive private trust store; no trust-all path.
- SEC-18: expanded database-URL secret detection and added query-parameter credential coverage.
- Audit filter now resolves the client IP through the shared `ClientIpResolver` instead of its own left-most-hop parser.
- R-01: shared `WorkspacePathGuard` and `NOFOLLOW_LINKS` path handling across all 14 path-taking tools; symlink-aware recursive walks and Dockerfile checks.
- R-02: `writeFile`/`editFile` validate the nearest existing ancestor, create nested package directories, re-check the real path, and return workspace-relative error text.
- R-03: generated scaffold no longer claims SEC-1 to SEC-10, rate limiting, or a login flow; `/api/auth/**` and `/api/public/**` are no longer permitted; `DB_PASSWORD` is required with no default; PostgreSQL binds to loopback; generated CI credentials are labelled CI-only; CLI and README describe scaffold limitations; `library/` sample regenerated.
- R-04: `runMaven` performs an offline build/test, then a read-only `bridge`-networked `dependency:go-offline` + `dependency:resolve-plugins` prefetch, then retries offline; prefetch is configurable, bounded, and only triggers on dependency-resolution failures.
- New/updated unit tests: `MavenGoalValidatorTest`, `ContainerReferenceValidationTest`, `ProjectManagerTest`, `SandboxServiceTest`, `SelfUpdateCommandTest`, `MtlsConfigTest`, `ClientIpResolverTest`, `TimeBoundedCharSequenceTest`, `EgressDnsResolverTest`, `EgressFilterTest`, `PromptSanitizerTest`, `SecretScannerTest`, `TerminalSupportTest`, `HttpClientConfigTest`, `FilterRegistrationTest`.

Verification after remediation:

- `mvn -B -ntp clean verify` — **BUILD SUCCESS** across all 12 modules; server 62 tests (1 skipped), CLI 35 tests, `engine-sandbox` 33 tests.
- Targeted server tests (ProjectManager, container validation, tool tests) — passed.
- `engine-sandbox` tests — 33 passed, including a regression test that the platform default Docker host is present in the default allow-list.
- Spring context tests (`FreshOrchestratorApplicationTests`, `JpaRepositoriesContextTest`, `ApiKeyRequirementsTest`) — passed.
- `npm audit` in `docs/` — 5 low advisories (all `deepmerge`).
- `npm run build` in `docs/` — **SUCCESS**.
- Server dependency tree — Jackson 2.21.7, OTel 1.67.0, Zipkin 1.64.0.
- Targeted SEC-07 to SEC-12 tests — 21 tests passed (mTLS, client IP, regex deadline, egress DNS/filter, filter registration).
- Full reactor after SEC-07 to SEC-13 (`mvn -B -ntp clean verify`) — **BUILD SUCCESS** across all 12 modules; 262 tests, 0 failures, 1 skipped (server 85, CLI 44).
- Full reactor after SEC-14 to SEC-18 (`mvn -B -ntp clean verify`) — **BUILD SUCCESS** across all 12 modules; 286 tests, 0 failures, 1 skipped (server 88, CLI 49).
- Targeted SEC-14 to SEC-18 tests — `SecretScannerTest` (16 cases), `TerminalSupportTest` (5), `HttpClientConfigTest` (3), plus `FilterRegistrationTest`, `ClientIpResolverTest`, and `SelfUpdateCommandTest` passed.
- Offline generated project (`companion generate --offline --non-interactive`, with `COMPANION_ASCII=1`) — `mvn -B -ntp verify` **BUILD SUCCESS**; Checkstyle 0 violations, SpotBugs 0 warnings.
- Working-tree secret scan — no `sk-or-v1-...` value outside excluded review/library material; `git diff --check` reports no whitespace errors.
- Aggregate CycloneDX SBOM — generated at `target/sbom.json`, schema 1.5, 222 components, including all 12 reactor modules.
- Full reactor after R-01/R-02 (`mvn -B -ntp clean verify`) — **BUILD SUCCESS**; 307 tests, 0 failures, 1 skipped.
- Full reactor after R-03/R-04 (`mvn -B -ntp clean verify`) — **BUILD SUCCESS**; 314 tests, 0 failures, 1 skipped (server 109, CLI 49, sandbox 42, templates 9).
- R-04 container-phase verification is unit-level only. Docker Desktop was paused on this host, so the networked prefetch and offline retry have not been exercised against a live daemon.

## 4. Remaining production-readiness blockers

1. **Sandbox image publication.** Build and publish `orchestrator/sandbox:1.0.0`; without it, sandboxed commands fail closed. The Docker engine pipe was unavailable on this host, so container-level verification is still outstanding.
2. **Library-level host execution.** `engine-core` `Coder.compileAndVerify` still shells out to host Maven. The server execution path does not invoke `Coder`, but consumers of the engine library should sandbox it before use.
3. **Deployment validation.** Docker Compose stack, mTLS stack, sandbox image, and a dockerized generated app remain untested. No live model-provider key has exercised online generation. The R-04 two-phase Maven prefetch is covered by mocked unit tests but has not run against a live Docker daemon; run it on a fresh host with an empty Maven cache before claiming the fresh-machine build path is verified.
4. **Supply chain execution and publication.** SBOM generation, dependency scanning, GPG signing, and provenance attestation are configured (SEC-13) but have not run in GitHub Actions from this environment. Maven Central publication and installer publication remain manual/external steps. Release secrets required: `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`; optional but recommended: `NVD_API_KEY`.
5. **Data durability.** In-memory cost tracking; no backup/retention automation; no queue/worker split.
6. **Release hygiene.** Version mismatch (`0.0.1-SNAPSHOT` vs docs `0.1.0`), stale status documents (`TESTING.md`, `ALL_ISSUES_SUMMARY.md`, `CEO_REVIEW.md`, `orchestrator-server/TODO.md`). The license inconsistency is resolved (Apache-2.0 across the tree).
7. **Credential rotation.** Rotate the OpenRouter key that appeared in `orchestrator-server/.idea/workspace.xml`. The value has been scrubbed from the working tree and is not in git history, but the exposed credential must still be revoked and reissued at the provider. A repository-wide scan now finds no `sk-or-v1-...` key.
8. **Scanner data is still online-only.** The Maven prefetch does not warm scanner data. Semgrep `auto`/registry configs need remote rules, Trivy needs its vulnerability database, and OWASP Dependency-Check needs NVD data. The tools fail closed inside the offline sandbox rather than running on the host. Operators must pre-warm those caches or use a dedicated scanner image before relying on scans from a fresh machine. This is the remaining part of the original "build/scan" finding.

## 5. Re-running the audit

To obtain a comprehensive (runtime-reproducing) CSO attestation, run the official helper on Linux with Docker available at a trusted `unix:///` endpoint:

```bash
gstack-cso doctor --repo /path/to/orchestrator
gstack-cso start --repo /path/to/orchestrator --comprehensive
```

Do not treat this report's Docker scanner results as a substitute for that run.
