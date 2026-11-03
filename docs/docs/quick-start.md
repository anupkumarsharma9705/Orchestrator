---
sidebar_position: 2
title: Quick Start
---

# Quick Start

> **Early access.** Package-manager installs and prebuilt releases are not
> published yet. Build the CLI from source; the offline path below is verified
> and does not need Docker or a model provider.

## Prerequisites

- **Java 21+** — [Download](https://adoptium.net/temurin/releases/)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **Git** — [Install](https://git-scm.com/downloads)
- **Docker 27+** (optional) — only for the generated project's PostgreSQL and
  for the server stack

```bash
java -version      # 21+
mvn -version       # 3.9+
git --version
```

## Build the CLI

```bash
git clone <repository-url> orchestrator
cd orchestrator
mvn -B -ntp -pl app-cli -am package -DskipTests
```

The executable jar is
`app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar`. The examples below abbreviate
that command to `companion`; see [Installation](/docs/installation) for a shell
alias.

## Generate your first project

```bash
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar generate \
  "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" \
  --name library --offline
```

The project is written to `./library` (or under `--project-root` when supplied).

### What offline mode produces

Offline generation is deterministic and uses the bundled templates. It does not
call a server or an LLM. You get a secure Spring Boot skeleton:

```text
library/
├── pom.xml
├── checkstyle.xml
├── spotbugs-exclude.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
├── .github/workflows/ci.yml
└── src/main/
    ├── java/com/example/library/
    │   ├── LibraryApplication.java
    │   ├── config/SecurityConfig.java
    │   ├── security/JwtAuthenticationFilter.java
    │   ├── exception/GlobalExceptionHandler.java
    │   └── {controller,service,repository,domain,dto,mapper}/package-info.java
    └── resources/application.yml
```

The skeleton includes JWT security, a global exception handler, PostgreSQL and
Flyway configuration, Checkstyle/SpotBugs gates, and CI. It does **not** contain
the Book entity, controller, service, repository, or tests — add those for your
domain. Online mode (server plus a model-provider key) is required for
LLM-generated domain code; it is early access and not covered by this quick
start.

## Build and run the generated project

```bash
cd library/library

# Build, test, Checkstyle, and SpotBugs
mvn -B -ntp verify

# Start PostgreSQL (generated compose file), then run the app
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
docker compose up -d postgres
mvn spring-boot:run
```

On Windows PowerShell, set the variable with
`$env:JWT_SECRET = "replace-with-a-random-secret-of-at-least-32-bytes"`.

Confirm the app is up:

```bash
curl http://localhost:8080/actuator/health
```

The health endpoint is public. Application endpoints are secured; requests
without a valid JWT return `401` until you add authentication and domain
endpoints.

## Next steps

- [Installation](/docs/installation) — Build and configuration details
- [Configuration](/docs/configuration) — Server URL, API keys, defaults
- [Offline Mode](/docs/offline-mode) — Generate without server
- [CLI Reference](/docs/cli-reference) — All commands and flags
