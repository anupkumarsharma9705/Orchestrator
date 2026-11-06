# Security Policy

## Supported versions

No release has been published yet. Security fixes land on the `main` branch;
rebuild from source after pulling the fix.

| Version | Supported |
|---------|-----------|
| `main` (source) | Yes |
| Published releases | None yet |

## Reporting a vulnerability

Please do **not** report security vulnerabilities through public GitHub issues.

Open a private security advisory on the repository's **Security** tab (GitHub
Security Advisories), or contact the maintainers privately if advisories are not
enabled on your fork.

Include:

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested fix

Reports are handled on a best-effort basis. No paid SLA or bug bounty is
available.

## Implemented security features

### Server

- **Authentication**: shared bootstrap API key via `Authorization: Bearer <key>`
  or `X-API-Key`; constant-time comparison; minimum 32 characters; no fallback
  key. Additional keys can be configured under `orchestrator.security.api-keys`
  with a narrower role set.
- **Authorization**: the bootstrap key carries `USER`, `APPROVER`, and `ADMIN`.
  Method security is enabled, so `@PreAuthorize` is enforced. Approvals require
  `APPROVER`; administration endpoints require `ADMIN`.
- **Identity**: task requesters and approval decisions are attributed to the
  authenticated principal. Caller-supplied requester/approver fields are
  ignored.
- **Route coverage**: a catch-all filter chain authenticates every route except
  the explicit public set (`/api/version`, health, and info).
- **Client IP**: `X-Forwarded-For` / `X-Real-IP` are honoured only when
  `orchestrator.security.trust-forwarded-headers` is enabled. Leave it off
  unless the server sits behind a proxy you control.
- **Transport**: HTTPS with mTLS; the server requires a client certificate.
- **Rate limiting**: Resilience4j per-endpoint limits plus failed-auth
  throttling per client IP.
- **Audit log**: JSON lines in `orchestrator-audit.log`. The audit and mTLS
  filters use servlet-valid prefix patterns (`/api/*`, `/actuator/*`), so API
  and actuator requests are actually recorded and `X-Client-Cert-DN` reaches
  downstream handlers.
- **Input validation**: Jakarta Bean Validation and request size limits.
- **Prompt sanitization**: pattern-based removal of prompt-injection attempts.
- **Workspace confinement**: a project's `rootPath` must be an absolute,
  canonical directory below `ORCHESTRATOR_WORKSPACE_ALLOWED_ROOTS`. API
  responses report workspace-relative paths instead of host paths.

### Generated projects

- Stateless JWT authentication; `JWT_SECRET` must be at least 32 bytes.
- Public access limited to health/info and explicitly public paths.
- CSRF disabled for stateless APIs; CORS restricted to an explicit allow-list.
- Checkstyle, SpotBugs/FindSecBugs, and an optional OWASP Dependency-Check
  profile.
- Dockerfile and Compose defaults for PostgreSQL and Flyway.

### Sandbox and verification

- Build, test, and scanner tools run inside a Docker sandbox; they do not
  execute target commands on the server host.
- The sandbox drops all capabilities, uses a read-only rootfs, runs as uid/gid
  `1001:1001`, and disables networking by default.
- Resource limits: 2 GB memory, 2 CPUs, 100 processes, bounded timeouts.
- Workspaces must live below `ORCHESTRATOR_SANDBOX_WORKSPACE_ROOTS`, the same
  allow-list as project registration.
- The sandbox **fails closed**. If the Docker daemon is unreachable, sandbox
  operations return an error; they do not fall back to host execution.
- The development Compose stack builds the sandbox image and mounts the host
  Docker socket plus a shared workspace volume so builds and scans work. This
  is **not** a production boundary: the Docker socket is root-equivalent host
  access. Production deployments must use a dedicated or remote Docker daemon
  over TLS and must not mount the host socket into the server container.
- Sandbox readiness is part of `/actuator/health`: a missing daemon, sandbox
  image, or shared workspace volume reports `DOWN`, and the Compose health
  check verifies the application health endpoint over mTLS instead of only
  completing a TLS handshake.
- `engine-verify` scanners include compile, tests, coverage, SpotBugs,
  Checkstyle, Semgrep, OWASP Dependency-Check, Trivy, and secrets. Container
  image scans export the image to a tar and scan it inside the sandbox rather
  than exposing the Docker socket to a container.

### Supply chain

- Sandbox image registry allow/deny rules and explicit-tag requirements.
- Gitleaks secret scanning in root CI.
- Release assets include a SHA-256 checksum that `install.sh` verifies.
- `companion self-update` requires a published checksum (`<jar>.sha256`,
  `checksums.txt`, or `SHA256SUMS`) and refuses to install a release without
  one.

## User guidance

- Use strong, unique API keys and rotate them.
- Keep provider keys and database passwords in environment variables or a
  secret manager, never in `config.yaml` or source control.
- Review generated code before deploying it.
- Never store provider keys in IDE workspace files (`.idea/`, `.vscode/`) or
  other ignored-but-local files. Ignore rules prevent commits, not disclosure
  through backups, screen sharing, or endpoint compromise.

### Rotating the OpenRouter key

If a key was ever pasted into a local IDE file, a log, or a shell history,
treat it as compromised even when it was never committed:

1. Revoke the key in the OpenRouter dashboard and create a replacement.
2. Update `.env` locally and the secret store used by CI/deployment.
3. Restart the server so the new value is loaded
   (`docker compose up -d --force-recreate orchestrator-server`).
4. Confirm `/api/health/providers` reports the `openrouter` provider as
   available.
5. Remove the old value from local IDE files, shell history, and backups.
   `.idea/` is already ignored by `.gitignore` and
   `orchestrator-server/.gitignore`; rotation is still required because
   ignoring a file does not revoke a leaked credential.
- Run `mvn -B -ntp verify` and the optional `-Psecurity` scan in the generated
  project.
- Back up PostgreSQL and the artifact directory before upgrading.

## Disclosure timeline

| Phase | Timeline |
|-------|----------|
| Acknowledgment | Best effort, typically within a few business days |
| Assessment | After reproduction |
| Fix | Depends on severity and maintainer availability |
| Public disclosure | After a fix is available |

## Compliance

This project has **no formal certification** (SOC 2, GDPR, OWASP ASVS, SLSA, or
otherwise). It provides controls that can support a compliance program, but you
must perform your own assessment.

## Security contacts

- **Security reports**: GitHub Security Advisories
- **PGP key**: available on request
- **Bug bounty**: not available

---

*Last updated: 2026-11-06*
