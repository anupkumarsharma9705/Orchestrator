---
sidebar_position: 6
title: CLI Reference
---

# CLI Reference

Complete reference for all `companion` commands and flags.

## Global Options

| Flag | Description |
|------|-------------|
| `-h, --help` | Show help |
| `-V, --version` | Print version |
| `-v, --verbose` | Enable verbose output |
| `--no-color` | Disable colored output |
| `--config <path>` | Config file path (default: `~/.companion/config.yaml`) |

```bash
companion --help
companion --version
companion --verbose generate "..."
companion --no-color config list
```

---

## Commands

### `generate` — Generate a new project

```bash
companion generate "Natural language description" --name <project-name> [options]
```

**Required:**
- Description (positional or `-d, --description`)
- `--name` — Project name

**Options:**

| Flag | Description |
|------|-------------|
| `-n, --name <name>` | Project name (required) |
| `-d, --description <desc>` | Natural language description |
| `--project-root <path>` | Output directory (default: current) |
| `--base-package <pkg>` | Base Java package (default: `com.example.<name>`) |
| `--output-format <fmt>` | `table`, `json`, `markdown` (default: `table`) |
| `--timeout <seconds>` | Request timeout (default: 600) |
| `--dry-run` | Show what would be generated |
| `--offline` | Generate without server |
| `--non-interactive` | Auto-approve, use defaults |

**Examples:**
```bash
# Basic
companion generate "REST CRUD for Book" --name library

# Full options
companion generate "REST API for Users with JWT" \
  --name user-service \
  --base-package com.myapp.users \
  --output-format json \
  --timeout 300

# Offline mode
companion generate "REST CRUD for Book" --name library --offline

# CI/CD (non-interactive)
companion generate "REST API for Orders" --name orders --non-interactive
```

---

### `verify` — Verify a project

```bash
companion verify <project-path> [options]
```

**Options:**
| Flag | Description |
|------|-------------|
| `--output-format <fmt>` | `table`, `json`, `markdown` |

```bash
companion verify ./my-project
companion verify ./my-project --output-format json
```

---

### `doctor` — Diagnose prerequisites

```bash
companion doctor [options]
```

Checks:
- Java 21+
- Maven 3.9+
- Docker 27+ (daemon reachable)
- Git
- Orchestrator server connectivity
- Workspace writable
- Disk space (>2GB)
- Port 8443 availability
- JVM heap (>=1GB)

**Options:**
| Flag | Description |
|------|-------------|
| `--output-format <fmt>` | `table`, `json`, `markdown` |

```bash
companion doctor
companion doctor --output-format json
```

---

### `config` — Manage configuration

```bash
companion config <subcommand> [options]
```

**Subcommands:**

| Subcommand | Description |
|------------|-------------|
| `list` | List all configuration values |
| `get <key>` | Get a specific value |
| `set <key> <value>` | Set a value |
| `path` | Show config file path |

```bash
companion config list
companion config list --output-format json
companion config get serverUrl
companion config set serverUrl https://api.example.com:8443
companion config set apiKey sk-abc123
companion config path
```

**Valid Keys:**
- `serverUrl` — Server URL (default: `https://localhost:8443`)
- `defaultProject` — Default project name
- `outputFormat` — `table`, `json`, `markdown`, `plain`
- `timeoutSeconds` — Timeout in seconds (default: 600)
- `apiKey` — API key for server auth
- `logLevel` — `DEBUG`, `INFO`, `WARN`, `ERROR`
- `noColor` — `true` or `false`

---

### `project` — Manage projects

```bash
companion project <subcommand> [options]
```

**Subcommands:**

| Subcommand | Description |
|------------|-------------|
| `list` | List all projects |
| `register <name> <path>` | Register a project |

```bash
companion project list
companion project register my-project ./my-project
```

---

### `task` — Manage tasks

```bash
companion task <subcommand> [options]
```

**Subcommands:**

| Subcommand | Description |
|------------|-------------|
| `get <task-id>` | Get task details |
| `list` | List tasks |
| `approve <task-id>` | Approve a task |
| `reject <task-id> --reason <reason>` | Reject a task |
| `logs <task-id> [--follow]` | View task logs |
| `artifacts <task-id>` | List task artifacts |

```bash
companion task get TASK-ABC123
companion task list --project my-project
companion task approve TASK-ABC123 --comment "Looks good"
companion task reject TASK-ABC123 --reason "Missing validation"
companion task logs TASK-ABC123 --follow
companion task artifacts TASK-ABC123
```

**Options:**
| Flag | Description |
|------|-------------|
| `--project <id>` | Filter by project |
| `--status <status>` | Filter by status |
| `--output-format <fmt>` | `table`, `json`, `markdown` |

---

### `self-update` — Update the CLI

> No release has been published yet, so this command cannot upgrade the CLI
> today. Rebuild from source until a release is announced.

```bash
companion self-update [options]
```

**Options:**
| Flag | Description |
|------|-------------|
| `--check-only` | Only check for updates |
| `--dry-run` | Show what would happen |
| `--force` | Force update |
| `--prerelease` | Include pre-releases |
| `--repo <owner/repo>` | GitHub repo (default: `akash/java-companion`) |

```bash
companion self-update --check-only
companion self-update --dry-run
companion self-update --force
```

---

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `1` | Command failed (connection, authentication, generation, or verification) |
| `2` | Invalid usage, missing arguments, or invalid input |

---

## Configuration File

Location: `~/.companion/config.yaml` (Linux/macOS) or `%USERPROFILE%\.companion\config.yaml` (Windows)

```yaml
serverUrl: "https://localhost:8443"
defaultProject: "my-project"
outputFormat: "table"
timeoutSeconds: 600
apiKey: "your-api-key"
logLevel: "INFO"
noColor: false
ssl:
  enabled: true
  trustStore: "/home/me/.companion/certs/truststore.p12"
  trustStorePassword: ""
  trustStoreType: "PKCS12"
  verifyHostname: true
  mtlsEnabled: true
  keyStore: "/home/me/.companion/certs/client-keystore.p12"
  keyStorePassword: ""
  keyStoreType: "PKCS12"
  keyAlias: "client"
```

---

## Environment Variables

| Variable | Config Key |
|----------|------------|
| `COMPANION_SERVER_URL` | `serverUrl` |
| `COMPANION_API_KEY` | `apiKey` |
| `COMPANION_SSL_TRUSTSTORE` | `ssl.trustStore` |
| `COMPANION_SSL_TRUSTSTORE_PASSWORD` | `ssl.trustStorePassword` |
| `COMPANION_SSL_KEYSTORE` | `ssl.keyStore` |
| `COMPANION_SSL_KEYSTORE_PASSWORD` | `ssl.keyStorePassword` |
| `COMPANION_SSL_MTLS_ENABLED` | `ssl.mtlsEnabled` |

---

## Examples

```bash
# Generate with all options
companion generate "REST API for Book store" \
  --name bookstore \
  --base-package com.example.bookstore \
  --output-format json \
  --timeout 300 \
  --non-interactive

# Verify project
companion verify ./my-project --output-format markdown

# Check health
companion doctor --output-format json

# Config management
companion config set apiKey sk-abc123
companion config list --output-format json

# Self-update
companion self-update --check-only
companion self-update --dry-run
```

---

## Getting Help

```bash
# General help
companion --help

# Command-specific help
companion generate --help
companion config --help
companion task --help
companion self-update --help
```