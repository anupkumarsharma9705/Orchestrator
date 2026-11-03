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
Docker socket, so the sandbox starts degraded.

```bash
cp .env.example .env
# fill in ORCHESTRATOR_API_KEY, POSTGRES_PASSWORD, CERT_PASSWORD
./scripts/dev-up.sh        # macOS / Linux
.\scripts\dev-up.ps1       # Windows PowerShell
```

Do not expose this stack to untrusted users: server tools such as
`runMavenBuild` execute on the server host.

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
| `NVIDIA_API_KEY` | Default model provider |
| `OPENROUTER_API_KEY` | OpenRouter key; also set `ORCHESTRATOR_MODEL_ROUTER_PROVIDERS_OPENROUTER_ENABLED=true` |
| `ORCHESTRATOR_PORT` | Host port for Compose (default `8443`) |
| `CERT_PASSWORD` | Password used by the Compose stack for both stores |

The server starts without provider keys. Generation requests then fail with
`No available providers`.

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
local file and runs build tools on its own host, so running replicas does not
give you a shared audit trail or isolation. Treat the current server as a
single trusted-operator service, and design a queue/worker split before scaling
out.

## Rollback

Redeploy the previous image or jar and keep the database migration compatible.
Because migrations are forward-only, take a backup before applying a release
that changes the schema.

## Next steps

- [Security](/docs/architecture/security) — mTLS, API keys, generated-project JWT
- [Configuration](/docs/configuration) — CLI configuration
- [Troubleshooting](/docs/advanced/troubleshooting) — Server and database issues
