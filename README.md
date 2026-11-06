# Java Companion Orchestrator

Generate Spring Boot REST APIs from natural language descriptions, with secure defaults, security scanning, testing, and auto-fixing.

> Status: early access. The supported install path today is building from source and running the local stack described below.

## Quick Start (from source)

Prerequisites:

- **Java 21+**
- **Maven 3.9+**
- **Git**
- **Docker 27+** with Docker Compose v2 and a running daemon (only for the full server stack)

### Generate without Docker or a model provider

This path uses the local templates and does not contact the server, Docker, or an LLM provider. It generates a runnable Spring Boot skeleton with secure defaults, build tooling, Docker/CI files, and documentation; add your domain entities, controllers, services, and tests afterward.

```bash
mvn -B -ntp -pl app-cli -am package -DskipTests

java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar generate \
  "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" \
  --name library --offline

cd library
mvn verify
```

`mvn verify` runs the generated tests, Checkstyle, and SpotBugs/FindSecBugs. The `security` profile adds OWASP Dependency-Check; the `coverage` profile enforces the coverage gate once the project has tests.

### Run the full local stack

```bash
# Build everything
mvn clean install

# One command: generate dev certificates, start PostgreSQL + the server,
# and write ~/.companion/config.yaml with mTLS and a generated API key.
# Windows:
./scripts/dev-up.ps1
# macOS / Linux:
./scripts/dev-up.sh

# Run the CLI (dev-up builds this jar automatically if it is missing)
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar doctor
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar generate \
  "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" --name library
```

`dev-up` creates a `.env` file with generated secrets if one does not exist. Never commit it. To configure values yourself, copy `.env.example` to `.env` and fill in at least `ORCHESTRATOR_API_KEY`, `POSTGRES_PASSWORD`, and `CERT_PASSWORD`.

The server starts without model-provider credentials, but server-backed generation fails with `No available providers`. OpenRouter is enabled by default: set `OPENROUTER_API_KEY` in `.env` and restart the stack with `docker compose up -d`. NVIDIA is optional (`NVIDIA_API_KEY`); Ollama is implemented but disabled by default. Kaggle is a disabled stub. The `--offline` generator above needs no key.

The compose stack builds `orchestrator/sandbox:1.0.0`, mounts the host Docker socket, and shares a named workspace volume between the server and sandbox containers. Build, test, and scanner tools therefore run in the sandbox. Mounting the Docker socket gives the server **root-equivalent control of the host**, so this mode is for local development only. For production, point the server at a dedicated or remote Docker daemon over TLS (`orchestrator.sandbox.docker-host` plus the Docker certificate paths) and do not mount the host socket. The sandbox still **fails closed**: when no daemon is reachable, tools return `Sandbox unavailable` instead of running on the server host.

Project registration is confined: a `rootPath` must be an absolute, existing directory below `ORCHESTRATOR_WORKSPACE_ALLOWED_ROOTS` (the compose stack uses `/workspaces`, which is the shared volume). Keep `ORCHESTRATOR_SANDBOX_WORKSPACE_ROOTS` aligned with the same directories. The server health endpoint reports sandbox readiness, so a missing Docker socket or sandbox image makes the container unhealthy.

Stop the stack with `docker compose down`. Remove data with `docker compose down -v`.

## Commands

### Generate a New Project
```bash
companion generate "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" --name library

# Non-interactive mode (auto-approve, use defaults)
companion generate "REST API for User management" --name users --non-interactive

# Dry run (show what would be generated without creating files)
companion generate "Simple TODO API" --name todo --dry-run

# Generate locally without a server, Docker, or model provider
companion generate "Simple TODO API" --name todo --offline
```

### Verify an Existing Project
```bash
# Verify a Spring Boot project
companion verify ./my-spring-project

# Verify with custom project name
companion verify ./my-project --project-name my-app
```

### Doctor (Prerequisite Check)
```bash
companion doctor
# Output: color-coded table with PASS/FAIL and remediation hints
```

### Project Management
```bash
# Register a project workspace
companion project register my-project ./path/to/project "My awesome project"

# List registered projects
companion project list
```

### Task Management
```bash
# Get task details
companion task get TASK-ABC123

# List tasks (with filters)
companion task list --project my-project --status COMPLETED

# Approve/reject task plans
companion task approve TASK-ABC123 --comment "Looks good"
companion task reject TASK-ABC123 --reason "Missing validation"

# View logs and artifacts
companion task logs TASK-ABC123 --follow
companion task artifacts TASK-ABC123 --download --output ./artifacts
companion task artifacts TASK-ABC123 --view requirements.md
```

### Configuration
```bash
# View all config
companion config list

# Get/set values
companion config get serverUrl
companion config set serverUrl https://localhost:8443
companion config set defaultProject my-project

# Show config file path
companion config path
```

## Output Formats
All commands support `--output-format`:
```bash
companion task list --output-format json
companion config list --output-format markdown
companion task get TASK-123 --output-format plain
```

## Configuration

Config file: `~/.companion/config.yaml` (created by `scripts/dev-up`)

```yaml
serverUrl: 'https://localhost:8443'
defaultProject: ''
outputFormat: 'table'
timeoutSeconds: 600
apiKey: '<generated by dev-up>'
logLevel: 'INFO'
noColor: false
ssl:
  enabled: true
  trustStore: '/path/to/certs/truststore.p12'
  trustStorePassword: '<CERT_PASSWORD>'
  trustStoreType: 'PKCS12'
  verifyHostname: true
  mtlsEnabled: true
  keyStore: '/path/to/certs/client-keystore.p12'
  keyStorePassword: '<CERT_PASSWORD>'
  keyStoreType: 'PKCS12'
  keyAlias: 'client'
```

Secrets can also be supplied through environment variables, which take precedence over the file:
`COMPANION_SERVER_URL`, `COMPANION_API_KEY`, `COMPANION_SSL_TRUSTSTORE`,
`COMPANION_SSL_TRUSTSTORE_PASSWORD`, `COMPANION_SSL_KEYSTORE`,
`COMPANION_SSL_KEYSTORE_PASSWORD`, `COMPANION_SSL_MTLS_ENABLED`.

The server refuses to start unless `ORCHESTRATOR_API_KEY` is set to a non-default
value of at least 32 characters. Certificate hostname verification cannot be
disabled by the CLI.

## How It Works

1. **Clarification** - Asks 5-8 clarifying questions to understand requirements
2. **Planning** - Generates a detailed task plan with dependencies
3. **Approval** - You review and approve the plan
4. **Code Generation** - Generates code task-by-task; verification runs in the Docker sandbox when a daemon is available
5. **Verification** - Runs compile, tests, security scans, style checks
6. **Auto-Fix** - Up to 5 rounds of automatic fixes for failures
7. **Artifacts** - Produces `requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md`

## Security

- **TLS everywhere** - the default server listener is HTTPS and requires a client certificate (mTLS)
- **No shared dev credentials** - the server has no fallback API key and the CLI has no fallback trust-all mode
- **Generated PKI** - `scripts/dev-up` creates a development CA with separate server and client certificates
- **Sandboxed engine verification** - `engine-verify` runs builds in Docker containers with no network, limited CPU/memory, and a read-only rootfs when a daemon is available
- **Server tools are host-executed** - `runMavenBuild`, `runMavenTest`, and the scanner tools run directly on the server host; the compose stack has no Docker socket, so sandboxing is degraded there
- **Path validation** - prevents path traversal attacks
- **Secret scanning** - detects and redacts secrets in generated code
- **Prompt injection defense** - sanitizes all LLM inputs

## Architecture

```
companion (CLI)
    │
    ├── generate → Clarifier → Planner → Coder → Sandbox → Verifier → FixLoop
    ├── verify  → Sandbox → Verifier
    ├── doctor  → System checks
    └── config  → YAML config management

Sandbox: Docker container (JDK + Maven + Scanners) when a Docker daemon is available
```

## Development

```bash
# Build all modules
mvn clean install

# Run tests
mvn test

# Build the CLI JAR
mvn package -pl app-cli -am -DskipTests

# Run the CLI locally
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar --help
```

### Server-only workflow

```bash
export ORCHESTRATOR_API_KEY="$(openssl rand -hex 32)"
export CERT_PASSWORD="$(openssl rand -hex 24)"
CERT_PASSWORD="$CERT_PASSWORD" ./scripts/generate-dev-certs.sh

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/orchestrator
export SPRING_DATASOURCE_USERNAME=companion
export SPRING_DATASOURCE_PASSWORD='<postgres password>'
export SSL_KEY_STORE="$PWD/certs/server-keystore.p12"
export SSL_TRUST_STORE="$PWD/certs/truststore.p12"
export SSL_KEY_STORE_PASSWORD="$CERT_PASSWORD"
export SSL_TRUST_STORE_PASSWORD="$CERT_PASSWORD"

mvn -pl orchestrator-server spring-boot:run
```

## Continuous Integration

`.github/workflows/ci.yml` runs on pushes, pull requests, and manual dispatch:

- `mvn -B -ntp clean verify` on Ubuntu and Windows with Java 21.
- Offline CLI generation followed by `mvn -B -ntp verify` in the generated project.
- Gitleaks secret scanning using `.gitleaks.toml`.

## Release Process

1. Tag a release: `git tag v0.1.0 && git push origin v0.1.0`
2. GitHub Actions verifies the reactor, then creates the release
3. Release assets include `companion.jar`, its SHA-256 checksum, and `install.sh`

## License

MIT License - see LICENSE file for details.