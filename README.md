# Java Companion Orchestrator

A CLI tool that generates a production-ready Spring Boot REST API project from a natural language description, with built-in security scanning, testing, and auto-fixing.

## Quick Start

```bash
# Install via script (recommended)
curl -sSL https://github.com/owner/java-companion/releases/latest/download/install.sh | bash

# Or download manually
curl -sSL https://github.com/owner/java-companion/releases/latest/download/companion.jar -o companion.jar

# Generate a project
companion generate "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" --name library

# Verify an existing project
companion verify ./my-spring-project
```

## Prerequisites

- **Java 21+** (required for running the CLI)
- **Maven 3.9+** (required for building generated projects)
- **Docker 27+** (required for sandboxed builds/tests)
- **Git** (for version control)

Verify prerequisites:
```bash
companion doctor
```

## Commands

### Generate a New Project
```bash
companion generate "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" --name library

# Non-interactive mode (auto-approve, use defaults)
companion generate "REST API for User management" --name users --non-interactive

# Dry run (show what would be generated without creating files)
companion generate "Simple TODO API" --name todo --dry-run
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
companion config set serverUrl http://localhost:8080
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

Config file: `~/.companion/config.yaml`

```yaml
companion:
  server-url: http://localhost:8080
  default-project: ""
  output-format: table
  timeout-seconds: 600
  api-key: ""
  log-level: INFO
  no-color: false
```

## How It Works

1. **Clarification** - Asks 5-8 clarifying questions to understand requirements
2. **Planning** - Generates a detailed task plan with dependencies
3. **Approval** - You review and approve the plan
4. **Code Generation** - Generates code task-by-task in a secure sandbox
5. **Verification** - Runs compile, tests, security scans, style checks
6. **Auto-Fix** - Up to 5 rounds of automatic fixes for failures
7. **Artifacts** - Produces `requirements.md`, `plan.md`, `fixes.md`, `vulnerabilities.md`, `report.md`

## Security

- **Sandboxed execution** - All builds run in isolated Docker containers
- **No host access** - Containers have no network, limited CPU/memory, read-only rootfs
- **Path validation** - Prevents path traversal attacks
- **Secret scanning** - Detects and redacts secrets in generated code
- **Prompt injection defense** - Sanitizes all LLM inputs

## Architecture

```
companion (CLI)
    │
    ├── generate → Clarifier → Planner → Coder → Sandbox → Verifier → FixLoop
    ├── verify  → Sandbox → Verifier
    ├── doctor  → System checks
    └── config  → YAML config management

Sandbox: Docker container (JDK + Maven + Scanners)
```

## Development

```bash
# Build all modules
mvn clean install

# Run tests
mvn test

# Build CLI JAR
mvn package -pl app-cli -am -DskipTests

# Run CLI locally
java -jar app-cli/target/companion.jar --help
```

## Release Process

1. Tag a release: `git tag v0.1.0 && git push origin v0.1.0`
2. GitHub Actions builds, tests, creates release, uploads JAR
3. Install script automatically updated in release assets

## License

MIT License - see LICENSE file for details.