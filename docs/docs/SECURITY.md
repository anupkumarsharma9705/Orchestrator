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

- **Authentication**: shared API key via `Authorization: Bearer <key>` or
  `X-API-Key`; constant-time comparison; minimum 32 characters; no fallback key.
- **Transport**: HTTPS with mTLS; the server requires a client certificate.
- **Rate limiting**: Resilience4j per-endpoint limits.
- **Audit log**: JSON lines in `orchestrator-audit.log`.
- **Input validation**: Jakarta Bean Validation and request size limits.
- **Prompt sanitization**: pattern-based removal of prompt-injection attempts.

### Generated projects

- Stateless JWT authentication; `JWT_SECRET` must be at least 32 bytes.
- Public access limited to health/info and explicitly public paths.
- CSRF disabled for stateless APIs; CORS restricted to an explicit allow-list.
- Checkstyle, SpotBugs/FindSecBugs, and an optional OWASP Dependency-Check
  profile.
- Dockerfile and Compose defaults for PostgreSQL and Flyway.

### Sandbox and verification

- Docker sandbox drops all capabilities, uses a read-only rootfs, runs as
  uid/gid `1000:1000`, and disables networking by default.
- Resource limits: 2 GB memory, 2 CPUs, 100 processes, bounded timeouts.
- `engine-verify` scanners include compile, tests, coverage, SpotBugs,
  Checkstyle, Semgrep, OWASP Dependency-Check, Trivy, and secrets.
- The compose stack does **not** mount the Docker socket, so sandboxing is
  degraded there. Server tools such as `runMavenBuild` execute on the host; do
  not expose the server to untrusted users.

### Supply chain

- Sandbox image registry allow/deny rules and explicit-tag requirements.
- Gitleaks secret scanning in root CI.
- Release assets include a SHA-256 checksum that `install.sh` verifies.

## User guidance

- Use strong, unique API keys and rotate them.
- Keep provider keys and database passwords in environment variables or a
  secret manager, never in `config.yaml` or source control.
- Review generated code before deploying it.
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

*Last updated: 2026-11-03*
