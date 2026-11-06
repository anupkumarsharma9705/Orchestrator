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
| **Application** | API-key auth, RBAC, input validation | Spring Security method security, Jakarta Validation |
| **Code** | ReDoS protection, secret scanning | Bounded regexes, secret scanner, Gitleaks in CI |
| **Supply chain** | Image allow/deny rules, checksums | Sandbox registry rules, mandatory release SHA-256 |

## Threat model

| Threat | Likelihood | Impact | Mitigation |
|--------|------------|--------|------------|
| Prompt injection | Medium | High | Prompt sanitizer + input size limits |
| Code execution in sandbox | Low | Critical | Dropped caps, read-only rootfs, no network |
| Path traversal | Low | High | Canonical workspace allow-list for projects and sandbox mounts |
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

The sandbox image runs as uid/gid `1001:1001` so it can share the workspace
volume with the server container. Workspaces are only mounted when
they resolve below a configured `workspace-roots` entry. If Docker is
unreachable, sandbox operations **fail closed**: they return an error and never
fall back to running the command on the host.

When the server runs in a container (the Compose stack), it does not bind the
container path directly: the Docker daemon resolves bind sources on the host,
not in the server's mount namespace. Instead the server and sandbox share a
named workspace volume. The server inspects that volume to find its
daemon-side mountpoint, then binds only the project subdirectory into the
sandbox. The Compose stack also builds the sandbox image and mounts the host
Docker socket. That socket grants root-equivalent host control, so it is a
development-only convenience; production deployments should use a dedicated or
remote Docker daemon over TLS.

`/actuator/health` includes a `sandbox` contributor. It reports `DOWN` when the
daemon is unreachable, the configured sandbox image is missing, the shared
workspace volume is missing, or the workspace filesystem is unhealthy.

### Application

- **Server authentication**: shared bootstrap API key via
  `Authorization: Bearer <key>` or `X-API-Key`; constant-time comparison; at
  least 32 characters. Additional role-bearing keys can be configured under
  `orchestrator.security.api-keys`.
- **Authorization**: the bootstrap key carries `USER`, `APPROVER`, and `ADMIN`.
  `@EnableMethodSecurity` is active, so `@PreAuthorize` checks are enforced:
  task/artifact routes require `USER`, approvals require `APPROVER`, and
  administrative routes require `ADMIN`.
- **Identity**: task requester and approver identity come from the
  authenticated principal; request-body identity fields are ignored.
- **Route coverage**: a catch-all filter chain authenticates every path except
  the explicit public set (`/api/version`, `/actuator/health/**`,
  `/actuator/info/**`).
- **Client IP**: forwarding headers are ignored unless
  `orchestrator.security.trust-forwarded-headers=true`. When enabled, the
  right-most `X-Forwarded-For` hop is used, because the left-most value is
  client-controlled.
- **Workspace confinement**: `rootPath` must be an absolute canonical directory
  below `orchestrator.workspace.allowed-roots`; API responses expose
  workspace-relative paths rather than absolute host paths.
- **Generated projects**: stateless JWT authentication with `JWT_SECRET` of at
  least 32 bytes.
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
  workspace:
    allowed-roots: ["/srv/orchestrator/workspaces"]
  sandbox:
    docker-image: orchestrator/sandbox:1.0.0
    workspace-roots: ["/srv/orchestrator/workspaces"]
    # Containerized server: share a named volume instead of a host bind path.
    workspace-volume: orchestrator-workspace
    workspace-volume-root: /workspaces
    enable-network: false
    allowed-registries: ["docker.io", "ghcr.io"]
    denied-image-patterns: ["*:latest", "malicious/*"]
    cleanup-shared-volumes: false
```

`orchestrator.workspace.allowed-roots` and
`orchestrator.sandbox.workspace-roots` should point at the same directories so
project registration and sandbox mounting agree. Keeping the allow-list empty
disables project registration rather than accepting arbitrary host paths.

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

*Last reviewed: 2026-11-06*
