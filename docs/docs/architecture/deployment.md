---
sidebar_position: 10
title: Architecture - Deployment
---

# Architecture: Deployment

> **Early access.** No container image, Helm chart, or Kubernetes manifest is
> published. The commands below build and run the server from this repository.

## What the server needs

| Dependency | Purpose | Notes |
|------------|---------|-------|
| Java 21 | Runtime | The release jar is not published yet |
| PostgreSQL 16+ | Jobs, rounds, findings, costs | Required; H2 is not supported |
| TLS key store + trust store | HTTPS and mTLS | The server refuses insecure defaults |
| `ORCHESTRATOR_API_KEY` | REST API authentication | At least 32 characters |
| Workspace roots | Project registration and sandbox mounts | Usually `~/.orchestrator/workspaces`; must already exist |
| Docker daemon | Build, test, and scanner sandbox | Sandbox fails closed when unreachable |
| Model provider key | Online generation | NVIDIA is the default |

The server also writes audit events to `orchestrator-audit.log` in its working
directory and stores artifacts under `orchestrator.store.base-path` (default
`.companion`). Mount persistent storage for both paths if you need to retain
them across container restarts.

Redis, S3/MinIO, Sentry, and message queues are **not** implemented.

## Build the server jar

```bash
mvn -B -ntp clean package
java -jar orchestrator-server/target/orchestrator-server-0.0.1-SNAPSHOT.jar
```

The jar requires the environment variables listed below. A local PostgreSQL
instance and a development PKI are created by `scripts/dev-up.sh` /
`scripts/dev-up.ps1`.

## Build a container image

```bash
docker build -f orchestrator-server/Dockerfile -t <registry>/java-companion:local .
docker push <registry>/java-companion:local
```

The image contains a JRE; it does not include PostgreSQL. Point it at a managed
database with `SPRING_DATASOURCE_URL`.

## Run with Docker Compose

The repository `docker-compose.yml` is a single-node development stack. It runs
PostgreSQL and the server with mTLS, and it intentionally does **not** mount the
Docker socket. Because the sandbox fails closed, build, test, and scanner tools
return an error in this stack instead of running on the server host. Provide a
sandbox-capable Docker daemon (or run those operations elsewhere) if you need
them.

```bash
cp .env.example .env
# fill in ORCHESTRATOR_API_KEY, POSTGRES_PASSWORD, CERT_PASSWORD
./scripts/dev-up.sh        # macOS / Linux
.\scripts\dev-up.ps1       # Windows PowerShell
```

Do not expose this stack to untrusted users. Register a project only under the
configured workspace allow-list.

## Configuration

Required:

| Variable | Description |
|----------|-------------|
| `ORCHESTRATOR_API_KEY` | Shared secret between CLI and server (32+ chars) |
| `SPRING_DATASOURCE_URL` | JDBC URL, for example `jdbc:postgresql://db:5432/orchestrator` |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SSL_KEY_STORE` | PKCS12 server key store |
| `SSL_TRUST_STORE` | PKCS12 trust store containing the client CA |
| `SSL_KEY_STORE_PASSWORD` | Key store password |
| `SSL_TRUST_STORE_PASSWORD` | Trust store password |

Optional:

| Variable | Description |
|----------|-------------|
| `ORCHESTRATOR_WORKSPACE_ALLOWED_ROOTS` | Comma-separated directories that may be registered as projects (default `~/.orchestrator/workspaces`) |
| `ORCHESTRATOR_SANDBOX_WORKSPACE_ROOTS` | Comma-separated directories that may be mounted into the sandbox; keep aligned with the workspace allow-list |
| `ORCHESTRATOR_SANDBOX_IMAGE` | Sandbox image (default `orchestrator/sandbox:1.0.0`) |
| `ORCHESTRATOR_SANDBOX_ENABLE_NETWORK` | Allow container networking (default `false`) |
| `ORCHESTRATOR_TRUST_FORWARDED_HEADERS` | Trust `X-Forwarded-For` / `X-Real-IP`; enable only behind a proxy you control (default `false`) |
| `NVIDIA_API_KEY` | Default model provider |
| `OPENROUTER_API_KEY` | OpenRouter key; also set `ORCHESTRATOR_MODEL_ROUTER_PROVIDERS_OPENROUTER_ENABLED=true` |
| `ORCHESTRATOR_PORT` | Host port for Compose (default `8443`) |
| `CERT_PASSWORD` | Password used by the Compose stack for both stores |

The server starts without provider keys. Generation requests then fail with
`No available providers`.

To give different clients narrower permissions, configure additional keys
under `orchestrator.security.api-keys` instead of sharing the bootstrap key:

```yaml
orchestrator:
  security:
    api-keys:
      - key: ${CI_API_KEY}
        subject: ci
        roles: [USER]
      - key: ${RELEASE_APPROVER_KEY}
        subject: release-approver
        roles: [USER, APPROVER]
```

## Health and metrics

```bash
# Public health endpoint
curl --cacert certs/ca.crt https://localhost:8443/actuator/health

# Prometheus metrics (authenticated)
curl --cacert certs/ca.crt \
  --cert-type P12 --cert "certs/client-keystore.p12:$CERT_PASSWORD" \
  -H "X-API-Key: $ORCHESTRATOR_API_KEY" \
  https://localhost:8443/actuator/prometheus
```

The OpenAPI schema is served at `/api-docs` and Swagger UI at `/swagger-ui.html`
when the server is running.

## Database migrations

Flyway migrations live under `db/orchestrator/migration` and run at startup.
Hibernate is configured with `ddl-auto: validate`; schema changes belong in a
new migration, never in entity annotations alone.

## Backup

Back up PostgreSQL with `pg_dump`:

```bash
pg_dump -h <host> -U companion -d orchestrator -Fc > orchestrator.dump
pg_restore -h <host> -U companion -d orchestrator --clean orchestrator.dump
```

Back up the artifact directory and audit log alongside the database if you rely
on them for retention or investigations.

## High availability and scaling

There is no tested multi-node deployment. The server keeps audit events in a
local file and relies on a Docker sandbox for build/test/scanner execution, so
running replicas does not give you a shared audit trail or a shared sandbox
namespace. Treat the current server as a single trusted-operator service, and
design a queue/worker split before scaling out.

## Rollback

Redeploy the previous image or jar and keep the database migration compatible.
Because migrations are forward-only, take a backup before applying a release
that changes the schema.

## Next steps

- [Security](/docs/architecture/security) — mTLS, API keys, generated-project JWT
- [Configuration](/docs/configuration) — CLI configuration
- [Troubleshooting](/docs/advanced/troubleshooting) — Server and database issues
