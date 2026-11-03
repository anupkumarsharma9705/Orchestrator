---
sidebar_position: 8
title: Architecture - Security
---

# Architecture: Security

> **Scope.** These are the controls implemented in this repository. No formal
> certification (OWASP ASVS, SLSA, SOC 2, etc.) has been obtained.

## Defense in depth

| Layer | Mechanism | Implementation |
|-------|-----------|----------------|
| **Network** | mTLS between CLI and server | Spring Security + PKCS12 key/trust stores |
| **Container** | Sandbox isolation | Dropped capabilities, read-only rootfs, no network, non-root user |
| **Application** | API-key auth, RBAC, input validation | Spring Security, Jakarta Validation |
| **Code** | ReDoS protection, secret scanning | Bounded regexes, secret scanner, Gitleaks in CI |
| **Supply chain** | Image allow/deny rules, checksums | Sandbox registry rules, release SHA-256 |

## Threat model

| Threat | Likelihood | Impact | Mitigation |
|--------|------------|--------|------------|
| Prompt injection | Medium | High | Prompt sanitizer + input size limits |
| Code execution in sandbox | Low | Critical | Dropped caps, read-only rootfs, no network |
| Path traversal | Low | High | Sandbox workspace boundary validation |
| Resource exhaustion | Medium | High | Container limits and timeouts |
| Supply chain attack | Low | Critical | Registry allow/deny list, release checksums |

## Controls by layer

### Network

- **mTLS**: the default server listener requires a client certificate.
- **TLS**: TLS 1.3 cipher suites are configured; the CLI always verifies the
  certificate chain and hostname. `ssl.verifyHostname: false` is ignored with a
  warning.
- **Rate limiting**: Resilience4j per-endpoint limits, configured in
  `application.yml` (API 100 requests/minute, actuator 200, health 500).

### Container sandbox

When a Docker daemon is available, `engine-sandbox` runs builds with:

```text
--cap-drop=ALL
--read-only
--network=none (default)
--memory=<configured>
--cpus=<configured>
--pids-limit=<configured>
--security-opt no-new-privileges
```

The sandbox image runs as uid/gid `1000:1000`. If Docker is unreachable, the
server starts in degraded mode and containerized verification is unavailable.

### Application

- **Server authentication**: shared API key via `Authorization: Bearer <key>`
  or `X-API-Key`; constant-time comparison; at least 32 characters.
- **Generated projects**: stateless JWT authentication with `JWT_SECRET` of at
  least 32 bytes.
- **Authorization**: role checks on protected server endpoints.
- **Input validation**: Jakarta Bean Validation plus request size limits.
- **Audit**: JSON lines written to `orchestrator-audit.log`.

### Code and dependency scanning

`engine-verify` includes scanners for compile, tests, coverage, SpotBugs,
Checkstyle, Semgrep, OWASP Dependency-Check, Trivy, and secrets. They run when
the corresponding binary is available; the sandbox image bundles them. Root CI
runs `mvn verify` and Gitleaks. Generated-project CI runs Checkstyle and
SpotBugs, with OWASP Dependency-Check behind an optional `NVD_API_KEY` gate.

## Sandbox configuration

```yaml
orchestrator:
  sandbox:
    enableNetwork: false
    allowedRegistries: ["docker.io", "ghcr.io"]
    deniedImagePatterns: ["*:latest", "malicious/*"]
    cleanupSharedVolumes: false
```

## Security operations

### Vulnerability management

1. **Detection**: Gitleaks in CI, Dependabot/OWASP scan options, scanner output
   from `engine-verify`.
2. **Triage**: severity classification based on the scanner output.
3. **Remediation**: patch and rebuild; releases are tag-driven.
4. **Disclosure**: private GitHub Security Advisory.

There is no paid SLA or bug bounty.

### Incident response

1. **Detection**: audit log, container logs, scanner findings.
2. **Containment**: stop the compose stack, rotate `ORCHESTRATOR_API_KEY`,
   provider keys, and database credentials.
3. **Investigation**: audit log, PostgreSQL records, artifact directory.
4. **Recovery**: redeploy the previous image/jar and restore the database.
5. **Postmortem**: document the root cause and follow-up work.

## Security contacts

- **Private disclosure**: GitHub Security Advisories on the repository
- **PGP key**: available on request
- **Bug bounty**: not available

---

*Last reviewed: 2026-11-03*
