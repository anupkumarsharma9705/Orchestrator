---
sidebar_position: 3
title: Installation
---

# Installation

> **Early access.** Homebrew, Scoop, Docker Hub, and prebuilt-JAR installers
> are not published yet. Building from source is the supported path today.

## Requirements

| Requirement | Version | Needed for |
|-------------|---------|------------|
| Java | 21+ | CLI and server |
| Maven | 3.9+ | Building from source |
| Git | any recent release | Cloning the repository |
| Docker | 27+ with Compose v2 | Optional local server stack and sandbox |

## Build from source

```bash
git clone <repository-url> orchestrator
cd orchestrator
mvn -B -ntp -pl app-cli -am package -DskipTests
```

The runnable CLI is:

```text
app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar
```

Verify the build:

```bash
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar --version
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar doctor
```

The rest of the documentation abbreviates that command to `companion`. To use
that name in your shell:

```bash
# macOS / Linux (use an absolute path)
alias companion='java -jar /absolute/path/to/orchestrator/app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar'
```

```powershell
# Windows PowerShell (use an absolute path)
function companion { java -jar "C:\absolute\path\to\orchestrator\app-cli\target\app-cli-0.0.1-SNAPSHOT-exec.jar" @args }
```

## Generate without Docker or a model provider

```bash
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar generate \
  "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" \
  --name library --offline
```

This path uses the bundled templates and does not contact a server, Docker, or
an LLM provider. It emits a secure Spring Boot skeleton: application class,
JWT security configuration and filter, global exception handler, PostgreSQL and
Flyway configuration, Dockerfile, Compose file, Checkstyle/SpotBugs setup, CI
workflow, and README. Add your domain entities, repositories, services,
controllers, and tests afterward.

## Optional: local server stack

With Docker 27+ and Compose v2 running:

```bash
# macOS / Linux
./scripts/dev-up.sh
```

```powershell
# Windows PowerShell
.\scripts\dev-up.ps1
```

The script starts PostgreSQL and the orchestrator with mTLS, generates
development certificates, and writes `~/.companion/config.yaml` so the CLI can
connect. See the repository README for the full server workflow and the
required `ORCHESTRATOR_API_KEY` (at least 32 characters).

Online generation additionally requires a model-provider key. OpenRouter is
enabled by default (`OPENROUTER_API_KEY`); NVIDIA (`NVIDIA_API_KEY`) and local
Ollama are optional. The server starts without provider keys, but generation
requests then fail with `No available providers`.

## Configuration

```bash
companion config set serverUrl https://localhost:8443
companion config set apiKey <your-api-key>
companion config set defaultProject my-project
companion config set outputFormat table
```

Configuration is stored in `~/.companion/config.yaml`.

## Verification

```bash
# Prerequisite checks
companion doctor

# Command help
companion generate --help

# Offline generation without writing files
companion generate "Hello World API" --name test --offline --dry-run
```

## Planned installers

The repository contains early Homebrew, Scoop, and release-workflow assets, but
no package has been published. Do not rely on `brew install companion`,
`scoop install companion`, Docker Hub images, or release download URLs until a
release is announced.

## Troubleshooting

### Java not found or too old

```text
Error: Java 21+ required. Found: 17.0.x
```

Install Java 21+ and make sure it is first on `PATH`.

### Docker not running

```text
Error: Cannot connect to Docker daemon
```

Start Docker Desktop (macOS/Windows) or the Docker service (Linux). Docker is
only required for the server stack and sandbox; offline generation works
without it.

### Permission denied on the Docker socket (Linux)

```bash
sudo usermod -aG docker "$USER"
# Log out and back in
```

### Maven build cannot resolve dependencies

The reactor is tested end to end with a normal (online) Maven build. If you are
building in an air-gapped environment, pre-populate the local Maven repository
first; the repository does not include a full offline dependency cache.

## Uninstall

Delete the cloned repository and the generated CLI jar:

```bash
rm -rf orchestrator
```

Remove local CLI configuration if you no longer need it:

```bash
rm -rf ~/.companion
```

## Next steps

- [Quick Start](/docs/quick-start) — Generate your first project
- [Configuration](/docs/configuration) — Server, API keys, defaults
- [Offline Mode](/docs/offline-mode) — Generate without server
