# Necessary Product Requirements — Java Companion Orchestrator

- **Status:** Requirements and gap-analysis baseline
- **Baseline commit:** `c4b921a`
- **Last updated:** 2026-11-07
- **Purpose:** Define what the product must do to be trusted by Java developers, usable by
  beginners and experts, defensible against attackers at scale, and viable as a
  high-volume service.

This document records the target requirements. It does not claim that the current
implementation satisfies them. Appendix A contains the initial implementation
assessment against the baseline commit; re-verify that assessment before each release.

---

## Part 1 — The Lead Java Backend Developer's View

A senior Java engineer will trust the tool only if it behaves like a good teammate
who knows the ecosystem.

### 1.1 Understand real Java projects, not just snippets

- Parse projects structurally — AST, symbol index, dependency graph, and Spring bean
  wiring — rather than treating source files as plain text. Acceptable implementations
  include JavaParser, Eclipse JDT, or the Language Server Protocol.
- Resolve what calls what, including cross-module references, so changes are based on
  the real project graph rather than string matching.
- Detect the build system and its versions:
  - Maven versus Gradle.
  - Single-module versus multi-module layouts.
  - JDK 8, 11, 17, 21, and 25.
  - Spring Boot 2 versus Spring Boot 3.
  - `javax.*` versus `jakarta.*`.
- Wrong-version code is the most common way AI tools lose Java developers' trust.
  Version detection must gate generation and migration output.
- Match existing codebase conventions instead of imposing new ones:
  - Package structure and naming.
  - Lombok or no Lombok.
  - MapStruct and other mapping conventions.
  - Exception-handling style.
  - Existing formatting and lint rules.
- Handle legacy and enterprise reality: old Java versions, monoliths, XML
  configuration, and internal or unusual frameworks.

### 1.2 Produce production-quality backend code

- Generate idiomatic modern Java: records, sealed types, pattern matching, virtual
  threads where appropriate, and constructor injection instead of field injection.
- Preserve correct layering: controller, service, repository.
- Keep DTOs separate from persistence entities.
- Include validation, global exception handling, and correct HTTP status codes.
- Handle databases correctly:
  - Flyway or Liquibase migrations.
  - N+1 query detection.
  - Index awareness.
  - Transaction boundaries.
  - Optimistic locking.
- Never silently write a schema change that can lose data. Destructive or ambiguous
  migrations require explicit approval.
- Get concurrency right: thread safety, `@Transactional` pitfalls such as
  self-invocation and checked exceptions, plus async and reactive code.
- Be secure by default: Spring Security configuration, an authorization check on every
  endpoint, no SQL concatenation, and safe deserialization.
- Include observability: structured logs, Micrometer metrics, health endpoints, and
  trace propagation.
- Generate tests that verify behavior, not coverage:
  - JUnit 5.
  - Mockito where appropriate.
  - Testcontainers for real databases.
  - Contract tests.
  - Tests that only inflate coverage are worse than no tests.

### 1.3 Be verifiable, not trusted blindly

- Compile, test, and lint every change before the user sees it.
- Show the real result, for example: "builds, 42 tests pass".
- Produce small, reviewable diffs rather than 40-file dumps.
- Provide one-click undo.
- Explain why an approach was chosen and show alternatives and trade-offs.
- Say "I'm not sure" and ask a clarifying question instead of guessing on ambiguous
  requirements.

### 1.4 Features senior developers will pay for

- Safe large-scale refactoring and migrations (Java 8 to 21, Spring Boot 2 to 3,
  `javax` to `jakarta`), performed in verified steps.
- Debugging from stack traces, heap dumps, thread dumps, and slow-query logs.
- Code-review mode that flags bugs, race conditions, and security issues in pull
  requests.
- API design help: OpenAPI generation, versioning, and backward-compatibility checks.
- Performance analysis: JFR, GC logs, and profiling hints.
- CI/CD help: Dockerfiles, Helm charts, and GitHub Actions pipelines.

---

## Part 2 — The Lead AI Engineer's View

### 2.1 Architecture

- Do not rely on a single giant prompt.
- Use a planner, specialized agents or tools (search, edit, build, test, review), and a
  verifier.
- Maintain explicit state and enforce step limits.
- Prefer deterministic tools wherever possible: the compiler, test runner, and static
  analyzers are the source of truth; the LLM proposes rather than judges.
- Route models by task:
  - A cheap, fast model for simple tasks such as explanations and boilerplate.
  - A stronger model for planning and hard debugging.
  - Automatic escalation when confidence is low.
- Model routing is required for sustainable unit economics at millions of users.

### 2.2 Context and retrieval

- Index codebases with a hybrid of code-aware chunking, embeddings, keyword search, and
  the symbol and call graph. Pure embedding search misses Java structure.
- Keep per-project memory of conventions, architecture notes, and past decisions.
- Project memory must be visible, editable, and deletable by the user.
- Use prompt caching and incremental indexing so large repositories stay fast and
  affordable.

### 2.3 Quality and evaluation

- Maintain a large, continuously growing benchmark of real Java tasks.
- Determine pass/fail automatically from compile and test results.
- Run the benchmark on every prompt, model, or tool change.
- Gate deploys on benchmark results.
- Track metrics that matter:
  - Compile success rate.
  - Tests-pass rate.
  - Edit acceptance rate.
  - Revert rate.
  - Time to a working result.
- Run shadow tests and A/B tests of new models on a small slice of traffic before
  rollout.
- Learn from real signals such as accepted versus rejected diffs while respecting
  privacy: opt-in and anonymized.

### 2.4 Reliability

- Support multiple model providers with automatic failover.
- Pin model versions.
- Keep a fast rollback path.
- Retry with capped loops. If the agent fails three times, it must stop, explain what
  it tried, and ask for help instead of burning tokens.
- Stream output so the tool feels fast even when the work is slow.
- Degrade gracefully: if the heavy agent is down, basic chat and explanations must
  still work.

### 2.5 Hallucination controls

- Verify every referenced class, method, and library against the real project and the
  real Maven Central, not against model memory.
- Ground answers in the user's code and official documentation.
- Cite the file and line, or link to the documentation, for factual claims.
- Reject outputs that do not compile before they ever reach the user.

---

## Part 3 — Protection from Attackers at Scale

At millions of users the product faces organized attackers, not just curious ones.

### 3.1 Attacks on users

- **Account takeover:** enforce MFA and passkeys, detect credential stuffing, send
  new-device alerts, and support session revocation.
- **Poisoned repositories:** a malicious repository, README, or dependency may try to
  hijack the agent. Treat all project content as untrusted data, separate it from
  instructions, and require approval for risky actions.
- **Malicious suggestions:** attackers may try to make the model recommend a
  backdoored library. Use package-reputation checks, typosquat detection, and an
  allowlist or warning for new or low-trust artifacts.
- **Leaking user code:** enforce strict tenant isolation, per-tenant encryption keys
  for enterprise customers, and no cross-user caching of prompts or code.

### 3.2 Attacks on the platform

- **Sandbox escape and abuse:** use microVMs such as Firecracker or gVisor, no network
  by default, strict resource limits, and hard time limits. Watch for crypto mining,
  port scanning, and DDoS launched from infrastructure controlled by the product.
- **Denial of wallet:** enforce per-user token and compute budgets, anomaly detection,
  and a global circuit breaker.
- **DDoS and bot floods:** use a CDN, WAF, adaptive rate limiting, and proof-of-work or
  CAPTCHA only when under attack.
- **Free-tier farming:** use device and behavior fingerprinting, phone or card
  verification for higher limits, and tiered trust.
- **Model extraction and jailbreaks:** monitor for systematic probing, throttle it, and
  keep system prompts free of secrets, because they will leak eventually.
- **Insider and supply-chain risk:** apply least-privilege staff access, audited
  production access, signed builds, and reproducible CI.

### 3.3 Assume breach

- Segment the network.
- Give every component the least possible privilege.
- Keep tamper-evident audit logs.
- Practice incident response with game days.
- Publish a clear security page and disclosure policy.

---

## Part 4 — Not Boring, and Easy for Anyone

Most developer tools are powerful but joyless or confusing. The product wins by being
useful and pleasant.

### 4.1 Adapt to the person, not the other way round

- **Beginner mode:** plain-language explanations, "what does this line do?", glossary
  tooltips, guided mini-projects, and gentle error explanations such as "this
  NullPointerException happens because...".
- **Pro mode:** terse, diff-first, keyboard-driven, no hand-holding, and configurable
  strictness.
- Let users set their level once, and adapt automatically from how they behave.

### 4.2 Make the first five minutes magical

- One click or one sentence to a running Spring Boot app with a live endpoint the user
  can hit.
- Time to first win must be under two minutes.
- Provide sample projects that teach real patterns, such as "build a REST API for a
  todo app" and "add JWT login".

### 4.3 Keep it engaging without being gimmicky

- Learning paths and challenges that build real skill: interview preparation, "fix this
  bug", "optimize this query".
- Progress tracking that matters.
- A "why" button on everything, so the tool teaches as it works.
- Visuals: auto-generated architecture diagrams, request flow, and entity-relationship
  diagrams.
- A clear personality that is friendly and concise, without fake enthusiasm or walls of
  text.
- Speed is mandatory: stream, cache, and show progress, because latency is the biggest
  killer of delight.

### 4.4 Trust-building UX

- Always show what will change before changing it.
- Provide a visible stop button and undo.
- Use plain-language permissions, for example: "this will run your tests in a secure
  sandbox, no network".
- Report errors and confidence honestly.
- Support accessibility: keyboard navigation, screen readers, and themes.
- Support multiple human languages, because users are global.

### 4.5 Meet developers where they live

- Ship IDE plugins: IntelliJ first, since most Java developers use it, then VS Code.
- Provide CLI, web, and GitHub or GitLab integration.
- A web-only tool will not win Java developers.
- Provide an API and plugin system so teams can add their own tools, rules, and company
  standards.

---

## Part 5 — Scaling to Millions

- Run stateless services.
- Use queue-based job processing.
- Autoscale sandbox pools with pre-warmed environments.
- Deploy across multiple regions for latency and resilience.
- Engineer costs aggressively: caching, small models by default, batching, and
  distillation of frequent tasks into cheaper models.
- Maintain strong free-tier economics, because beginners will be the largest group and
  the top of the funnel.
- Build community: documentation, examples, a public roadmap, and a place for users to
  share prompts, rules, and templates. Community compounds growth.
- Scale support: self-serve help, an in-product assistant, and a clear escalation path.

---

## Part 6 — What Makes It Win

Many tools can generate Java. This product wins on trust:

- Every output is compiled, tested, and scanned.
- Every action is reversible.
- Every user's code is private and safe.
- The experience is fast, adaptive to skill level, and teaches while it helps.

---

## Appendix A — Baseline Implementation Assessment

**Baseline:** `c4b921a` (2026-11-06). This is an initial assessment, not a release
certification. Re-verify each item before claiming support.

Legend: **Implemented** — present and usable; **Partial** — present but incomplete or
scope-limited; **Missing** — not implemented.

### A.1 Java backend developer requirements

| Requirement area | Status | Notes |
|---|---|---|
| AST, symbol index, call graph, or LSP-based project understanding | Missing | Generation is template- and prompt-driven; no structural Java analysis |
| Build-system and version detection (Maven/Gradle, multi-module, JDK, Boot 2/3, `javax`/`jakarta`) | Partial | Maven/Spring Boot oriented; Gradle is accepted in requests but not implemented end to end |
| Match existing project conventions | Partial | Template-level conventions exist; no analysis of an existing codebase |
| Legacy and enterprise support (old Java, XML config, monoliths) | Missing | Targets modern Spring Boot generation |
| Production-quality generated code (records, constructor injection, layering, validation, exception handling) | Partial | The generator emits a Spring Boot scaffold (build file, application class, security filter, exception handler, Docker/CI assets). Domain entities, controllers, services, repositories, migrations, and tests are not generated. |
| Database awareness (migrations, N+1, indexes, transactions, locking) | Partial | Flyway is configured in the scaffold, but no migrations are generated; N+1 detection and optimistic locking are not implemented |
| Concurrency correctness guidance | Missing | No dedicated handling for `@Transactional` pitfalls, async, or reactive correctness |
| Secure-by-default generated code | Partial | The scaffold requires `JWT_SECRET` and `DB_PASSWORD`, binds PostgreSQL to loopback, and does not leave auth paths open. It includes a bearer-token validation filter but no login endpoint, token issuance, refresh flow, user store, rate limiting, or authorization rules. Platform-side sandboxing is separate from the generated code. |
| Observability in generated projects | Partial | Actuator/OpenTelemetry exist in the platform; generated templates are not complete |
| Behavior-focused tests (JUnit 5, Mockito, Testcontainers, contract tests) | Partial | JUnit 5 and Mockito are present; Testcontainers/contract-test coverage is incomplete |
| Verified changes with visible results | Partial | Sandbox compile/test exists; no diff preview or one-click undo |
| Explain choices, alternatives, and uncertainty | Implemented | Clarifier and refiner stages exist |
| Large-scale refactoring and version migrations | Missing | Product generates new projects; migration workflows are not implemented |
| Stack-trace, heap-dump, thread-dump, and slow-query debugging | Missing | No debugging workflow |
| Pull-request code-review mode | Missing | No review mode |
| API design (OpenAPI, versioning, compatibility) | Partial | OpenAPI generation exists; versioning and compatibility checking do not |
| Performance analysis (JFR, GC logs, profiling) | Missing | Not implemented |
| CI/CD assistance (Dockerfile, Helm, GitHub Actions) | Partial | Generated templates include Dockerfile, Docker Compose, and GitHub Actions; no Helm chart is generated |

### A.2 AI engineering requirements

| Requirement area | Status | Notes |
|---|---|---|
| Planner, specialized agents/tools, verifier, explicit state, step limits | Implemented | Clarifier → planner → coder → verifier → fix loop |
| Deterministic compiler/test/analyzer feedback as source of truth | Implemented | Sandbox Maven build, tests, Checkstyle, SpotBugs, Semgrep, Trivy |
| Cost-aware model routing and automatic escalation | Partial | Profiles exist; routing is provider-based, not cost/confidence-based |
| Hybrid indexing (chunking, embeddings, keyword search, symbol/call graph) | Missing | File search only |
| Per-project, user-editable memory | Implemented | Project memory service with learned/documented entries |
| Prompt caching and incremental indexing | Missing | Not implemented |
| Continuous real-Java benchmark with compile/test pass/fail | Partial | Evaluation module exists; continuous benchmark gating is not established |
| Quality metrics (compile, tests, acceptance, revert, time-to-working) | Missing | Not collected |
| Shadow tests and A/B model rollouts | Missing | Not implemented |
| Opt-in learning from accepted/rejected diffs | Missing | Not implemented |
| Multi-provider failover, pinned versions, rollback | Implemented | Circuit breakers and fallback chains across providers |
| Capped retries that stop and ask after three failures | Partial | Fix-loop limits exist; explicit user escalation is incomplete |
| Streaming output | Partial | Backend supports streaming; CLI progress is limited |
| Graceful degradation when the heavy agent is unavailable | Missing | Core flow depends on the full agent stack |
| Verify symbols/libraries against the project and Maven Central | Missing | Verification happens at compile time, not before suggestion |
| Grounded answers with file/line or documentation citations | Missing | Not implemented |
| Reject non-compiling output before it reaches the user | Implemented | Sandbox compilation gates output |

### A.3 Attacker-resistance requirements

| Requirement area | Status | Notes |
|---|---|---|
| MFA, passkeys, credential-stuffing detection, session revocation | Missing | No end-user account system |
| Poisoned-repository and prompt-injection defense | Implemented | Prompt sanitization, untrusted-content handling, approval gates, fail-closed sandbox |
| Package reputation and typosquat detection | Missing | Dependency scanning exists; reputation checks do not |
| Tenant isolation and per-tenant encryption | Partial | Workspace isolation exists; multi-tenant encryption and isolation are not implemented |
| MicroVM sandboxing (Firecracker/gVisor) | Partial | Docker with read-only rootfs, dropped capabilities, no network, resource limits; not microVMs |
| Denial-of-wallet budgets and anomaly detection | Missing | Not implemented |
| CDN/WAF/adaptive rate limiting/PoW | Partial | Per-IP rate limiting exists; no CDN, WAF, or adaptive challenge |
| Free-tier farming defenses | Missing | Not implemented |
| Model-extraction and jailbreak monitoring | Missing | Not implemented |
| Insider and supply-chain controls (signed builds, reproducible CI) | Partial | GitHub Actions are pinned to SHAs; CI generates a CycloneDX SBOM and runs OWASP dependency-check; release artifacts are GPG-signed and provenance-attested when the release secrets exist. Maven Central publication is not configured. |
| Assume-breach controls (segmentation, tamper-evident audit, game days) | Missing | Audit logging exists but tamper-evidence and network segmentation do not |

### A.4 Usability requirements

| Requirement area | Status | Notes |
|---|---|---|
| Beginner mode and adaptive skill level | Missing | No beginner mode or behavior-based adaptation |
| Pro mode (terse, diff-first, keyboard-driven) | Partial | CLI options exist; diff-first workflow does not |
| Time to first win under two minutes | Partial | One-command offline generation works; full online flow depends on providers and sandbox |
| Sample projects and guided patterns | Missing | Not implemented |
| Learning paths, challenges, progress | Missing | Not implemented |
| "Why" explanations on demand | Missing | Not implemented |
| Architecture and flow diagrams | Missing | Not implemented |
| Friendly, concise personality | Implemented | CLI output follows this tone |
| Fast, streaming UX with progress | Partial | Sandbox cold starts and limited CLI progress remain |
| Change preview, stop, undo, plain-language permissions | Partial | Approvals exist; diff preview, stop, and undo are incomplete |
| Accessibility and localization | Missing | Not implemented |
| IntelliJ/VS Code plugins, web UI, GitHub/GitLab integration | Missing | CLI only |
| Extension API and plugin system | Partial | Tool interface is extensible; no plugin loading mechanism |

### A.5 Scaling requirements

| Requirement area | Status | Notes |
|---|---|---|
| Stateless services, queues, autoscaled sandbox pools, multi-region | Missing | Single-server deployment |
| Cost engineering (caching, small-model default, batching, distillation) | Missing | Not implemented |
| Free-tier economics and tiered limits | Missing | No accounts or tiering |
| Community platform (docs, examples, roadmap, shared prompts/rules/templates) | Partial | Documentation and repository exist; sharing platform does not |
| Scalable support (self-serve, in-product assistant, escalation) | Missing | Not implemented |

---

## Appendix B — Release Gate

The product must not be described as a companion for millions of Java developers until
the following are true:

1. It can analyze and modify an existing Java codebase structurally, not just generate
   new projects.
2. Generated and modified code is compiled, tested, and scanned before the user sees it.
3. Every action that changes user code is previewable, reversible, and auditable.
4. The product has a user-facing identity and authorization model suitable for
   multi-tenant operation.
5. Sandboxing is at least microVM-grade for untrusted code at scale.
6. Model routing, benchmarking, and cost controls exist and are enforced in production.
7. IDE integration exists, with IntelliJ first.
8. Security controls in Part 3 are implemented or explicitly documented as out of scope
   for the release.

---

## Appendix C — Critical Security Remediation (SEC-01 to SEC-06)

| ID | Issue | Remediation | Remaining external action |
|---|---|---|---|
| SEC-01 | Sandbox disabled in Compose | Compose builds `orchestrator/sandbox:1.0.0`, mounts the Docker socket, and shares a named workspace volume. Sandbox readiness is part of `/actuator/health`. | None for local development. Production must use a dedicated or remote TLS Docker daemon. |
| SEC-02 | Invalid audit/mTLS filter patterns | Filters use servlet-valid `/api/*` and `/actuator/*` patterns, with a regression test. | None. |
| SEC-03 | Zero default LLM providers | OpenRouter is enabled by default; compose passes `ORCHESTRATOR_MODEL_ROUTER_PROVIDERS_OPENROUTER_ENABLED=true`; provider status remains available at `/api/health/providers`. | Set `OPENROUTER_API_KEY` (or another provider key). |
| SEC-04 | TLS-only health check | Compose health check calls `/actuator/health` over mTLS and requires `"status":"UP"`. | None. |
| SEC-05 | Flyway warning noise | PostgreSQL sessions use `client_min_messages=warning`; tests override the init SQL for H2. | None. |
| SEC-06 | OpenRouter key in `.idea/workspace.xml` | Local IDE value is scrubbed; `.idea/` is ignored in both `.gitignore` files; rotation steps are documented in `docs/docs/SECURITY.md`. | Revoke and replace the key in the OpenRouter dashboard and CI secrets. |

---

## Appendix D — Security Hardening (SEC-07 to SEC-13)

| ID | Issue | Remediation | Remaining external action |
|---|---|---|---|
| SEC-07 | mTLS identity spoofing through `X-Client-Cert-DN` | The header is ignored unless `mtls.trust-proxy-header=true`; trusted values are length-capped and rejected if they contain control characters or lack `=`; regression tests cover opt-in and default denial. | Enable the flag only behind a proxy that terminates TLS and overwrites the header on every request. |
| SEC-08 | Self-update transport trust | Update traffic is HTTPS-only and restricted to GitHub hosts; redirects are re-validated; `owner/repo` is validated; an optional PKCS12/JKS trust store is supported via `--trust-store`, with no trust-all fallback. | Distributors using a private CA must provide a trust store and its password through the documented property/environment variable. |
| SEC-09 | Rate-limit bypass via spoofed forwarding headers | A shared `ClientIpResolver` ignores forwarding headers by default and uses the right-most `X-Forwarded-For` hop only when proxy trust is enabled; API-key throttling and endpoint limits use the same resolver. | None. |
| SEC-10 | Secret-scanner regex denial of service | `TimeBoundedCharSequence` enforces a wall-clock deadline inside the regex engine; `SecretScanTool` skips and logs a pattern that exceeds its budget. | None. |
| SEC-11 | Detection/sanitization normalization drift | `PromptSanitizer` uses one NFC normalization and one shared match set for `sanitize()` and `hasInjection()`; both honor the same allow-list. | None. |
| SEC-12 | Unbounded egress DNS resolution | `EgressDnsResolver` bounds lookups (2 s default), caches positive/negative results, and fails closed on timeout; configuration is available through system properties or environment variables. | Tune the timeout and cache TTL for the deployment's DNS environment. |
| SEC-13 | Missing supply-chain controls | CI generates a CycloneDX aggregate SBOM and runs OWASP dependency-check; the release workflow signs the jar, SBOM, checksums, and installer with GPG, verifies signatures, and attests build provenance. | Configure `GPG_PRIVATE_KEY` and `GPG_PASSPHRASE`; optionally `NVD_API_KEY`. Maven Central publication remains out of scope until production coordinates and repository credentials exist. |

---

## Appendix E — Security Hardening (SEC-14 to SEC-18)

| ID | Issue | Remediation | Remaining external action |
|---|---|---|---|
| SEC-14 | `CertificatePinningManager.generatePinForHost` always threw `UnsupportedOperationException`. | Dead method and its unused imports removed; the working TLS pin generator remains. | None. |
| SEC-15 | Windows CMD showed `?` instead of box-drawing and status glyphs. | `TerminalSupport` tests the actual stdout charset and falls back to ASCII for headings, tables, spinners, and status markers. `COMPANION_ASCII=1|true|yes` forces ASCII. | None. |
| SEC-16 | No tracked `LICENSE`; README said MIT while distribution manifests said Apache-2.0. | Apache-2.0 `LICENSE` added; README, root POM, server POM, docs `package.json`, and lockfile aligned on Apache-2.0. | None. |
| SEC-17 | Outbound HTTP client had no explicit TLS configuration or private-CA support. | `HttpClientConfig` uses an explicit `SSLContext` over the JVM default CAs and can add a PKCS12/JKS trust store through `orchestrator.http.tls.trust-store*` (`ORCHESTRATOR_HTTP_TLS_TRUST_STORE*`). The store is additive; hostname verification stays enabled and there is no trust-all mode. | Deployments using a private CA must provide the trust store and password; a missing or unreadable store fails startup. |
| SEC-18 | Database-URL secret detection missed several schemes and query-parameter passwords. | Patterns now cover `jdbc:` plus PostgreSQL, MongoDB/SRV, MySQL, MariaDB, Redis/rediss, SQL Server, Oracle, SQLite, DB2, H2, ClickHouse, Snowflake, Redshift, Trino, Presto, and Neo4j URLs, plus `password=`/`pwd=` query parameters. | None. |
