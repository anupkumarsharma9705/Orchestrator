# API Contracts
## Java Companion Orchestrator — v1 CLI MVP

> **Base URL**: Local CLI execution (no HTTP server in v1)
> **Protocol**: Local process execution via `companion.jar`
> **Data Format**: JSON (CLI `--json` flag), Markdown (artifact files)

---

## 1. CLI Command Reference

### 1.1 Global Options
| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--help` | `-h` | Show help | - |
| `--version` | `-v` | Print version | - |
| `--verbose` | `-v` | Verbose output | false |
| `--quiet` | `-q` | Suppress non-error output | false |
| `--json` | `-j` | Machine-readable JSON output | false |
| `--format` | `-f` | Output format: `json`, `yaml`, `text` | `text` |
| `--yes` | `-y` | Auto-approve all prompts | false |
| `--max-rounds` | `-r` | Max fix rounds (1-5) | 5 |
| `--budget` | `-b` | Budget ceiling USD | 0.50 |
| `--model` | `-m` | LLM model override | config default |
| `--offline` | `-o` | Use local Ollama model | false |
| `--config` | `-c` | Custom config file path | `~/.companion/config.yml` |
| `--project-dir` | `-p` | Project directory | current dir |

---

## 2. Command Specifications

### 2.1 `companion generate`
Generate a new Spring Boot project from natural language spec.

```bash
companion generate "SPEC" [OPTIONS]
```

**Arguments:**
| Arg | Required | Description |
|-----|----------|-------------|
| `SPEC` | Yes | Natural language specification (quoted) |

**Options:**
| Flag | Short | Description | Default |
|------|-------|-------------|---------|
| `--name` | `-n` | Project name (directory name) | Auto-generated |
| `--output` | `-o` | Output directory | `./<name>` |
| `--package` | `-p` | Java base package | `com.example.<name>` |
| `--build-tool` | `-b` | Build tool: `maven` | `maven` |
| `--java-version` | `-j` | Java version: `21` | `21` |
| `--spring-boot` | `-s` | Spring Boot version | `3.4.x` |
| `--no-verify` | | Skip verification rounds | false |
| `--max-rounds` | `-r` | Max fix rounds (1-5) | 5 |
| `--budget` | `-b` | Budget ceiling USD | 0.50 |
| `--yes` | `-y` | Auto-approve plan | false |
| `--dry-run` | | Show plan without executing | false |

**Output (text mode):**
```bash
$ companion generate "REST CRUD for Book entity with author, genre, ISBN" --name library
🔍 Clarifying questions (5):
  1. Database: PostgreSQL (default) / H2 / MySQL? [PostgreSQL]
  2. Auth: Spring Security + JWT / Session / None? [JWT]
  3. API Style: REST / GraphQL? [REST]
  4. Tests: JUnit 5 + Testcontainers / JUnit 5 only? [JUnit 5 + Testcontainers]
  5. Packaging: JAR / WAR? [JAR]

✓ Requirements.md generated
📋 Plan generated (12 tasks):
  T-001: Create Book entity + JPA mapping
  T-002: Create BookRepository
  ...
  T-012: Write README + Dockerfile

❯ Approve plan? [Y/n] y
🏗 Building...
Round 1: Build + Verify ████████████ 100% (45s)
  ✓ Compile OK
  ✓ Tests: 24 passed
  ✓ Security: 0 HIGH, 2 MEDIUM, 5 LOW
Round 2: Fix ████████████ 100% (12s)
  ✓ Fixed 2 MEDIUM (SQL injection risk, missing @Valid)
Round 3: Verify ████████████ 100% (8s)
  ✓ All clear

✅ Done! Project at ./library
📁 Artifacts: .companion/{requirements.md,plan.md,fixes.md,vulnerabilities.md,report.md}
```

**Output (JSON mode `--json`):**
```json
{
  "jobId": "job-abc123",
  "status": "COMPLETED",
  "rounds": 3,
  "durationSec": 65,
  "costUsd": 0.0234,
  "artifacts": {
    "requirements": ".companion/requirements.md",
    "plan": ".companion/plan.md",
    "fixes": ".companion/fixes.md",
    "vulnerabilities": ".companion/vulnerabilities.md",
    "report": ".companion/report.md"
  },
  "stats": {
    "compileSuccess": true,
    "testsPassed": 24,
    "testsFailed": 0,
    "findingsBySeverity": { "HIGH": 0, "MEDIUM": 0, "LOW": 0 }
  }
}
```

---

### 2.2 `companion verify`
Run verification (scanners + tests) on existing Spring Boot project.

```bash
companion verify <FOLDER> [OPTIONS]
```

**Options:**
| Flag | Description | Default |
|------|-------------|---------|
| `--scanners` | Comma-separated: `semgrep,trivy,depcheck,spotbugs,checkstyle,secret,coverage` | all |
| `--fail-on` | Fail exit code on severity: `HIGH`, `MEDIUM`, `LOW` | `HIGH` |
| `--format` | Output: `text`, `json`, `sarif` | `text` |
| `--output` | Write report to file | stdout |

**Output:**
```bash
$ companion verify ./my-project --scanners semgrep,spotbugs,depcheck
🔍 Running verification on ./my-project
📦 Compile: OK (2.3s)
🧪 Tests: 42 passed, 0 failed (15.2s)
🔒 Security: 1 HIGH (SQL injection), 3 MEDIUM, 8 LOW
📦 Dependencies: 2 vulnerable (CVE-2024-xxxx, CVE-2023-yyyy)
📝 Style: 12 Checkstyle warnings
📊 Coverage: 78% (threshold 80%)

⚠️  Verification FAILED: 1 HIGH finding
📄 Report: .companion/verification-report.json
```

---

### 2.3 `companion fix`
Run fix rounds on existing project.

```bash
companion fix <FOLDER> [OPTIONS]
```

**Options:**
| Flag | Description | Default |
|------|-------------|---------|
| `--max-rounds` | Max fix rounds (1-5) | 3 |
| `--budget` | Budget ceiling USD | 0.25 |
| `--only` | Comma-separated: `security,tests,style,deps` | all |
| `--dry-run` | Show fixes without applying | false |

---

### 2.4 `companion status`
Check job status.

```bash
companion status <JOB_ID> [--json]
```

**Output:**
```bash
Job: job-abc123
Status: VERIFYING (round 3/5)
Progress: ████████░░ 60%
Cost: $0.012 / $0.50
Findings: 3 OPEN (1 HIGH, 2 MEDIUM)
ETA: ~45s
```

---

### 2.5 `companion approve`
Approve or reject pending plan.

```bash
companion approve <JOB_ID> [--reject] [--note "reason"]
```

---

### 2.6 `companion config`
Manage configuration.

```bash
companion config <get|set|list> [KEY] [VALUE]
```

Examples:
```bash
$ companion config list
$ companion config set nvidia.api-key "nvapi-xxx"
$ companion config get nvidia.api-key
$ companion config set budget.default 1.00
```

---

### 2.7 `companion doctor`
Diagnose prerequisites.

```bash
companion doctor [--fix]
```

**Output:**
```bash
$ companion doctor
🔍 Diagnosing environment...

✅ Java 21.0.5 (Temurin)
✅ Maven 3.9.6
✅ Docker 27.1.1 (running)
✅ Git 2.45.1
❌ NVIDIA_API_KEY not set (required for generation)
✅ ~/.companion/config.yml exists
✅ SQLite 3.46.0
⚠️  Semgrep 1.100.0 (latest: 1.101.0)
⚠️  Trivy 0.55.0 (latest: 0.56.0)

💡 Run `companion doctor --fix` to auto-update tools
```

---

### 2.8 `companion config set provider`
```bash
companion config set provider <nvidia|openrouter|ollama>
```

---

## 3. Artifact File Specifications

### 3.1 requirements.md
```markdown
# Requirements: library
**Version:** 1.0 | **Date:** 2026-10-26 | **Original Request:** REST CRUD for Book entity...

## Original Request
REST CRUD for Book entity with author, genre, ISBN, pagination, validation

## Clarification Questions & Answers
| # | Question | Answer |
|---|----------|--------|
| 1 | Database | PostgreSQL |
| 2 | Auth | Spring Security + JWT |
| 3 | API Style | REST |
| 4 | Tests | JUnit 5 + Testcontainers |
| 5 | Packaging | JAR |

## Requirements
### Functional Requirements
- R-001: CRUD operations for Book entity
- R-002: Pagination (page, size, sort)
- R-003: Validation (ISBN format, required fields)

### Security Requirements
- SEC-001: JWT authentication on all endpoints
- SEC-002: Role-based access (ADMIN, USER)

### Assumptions
- Java 21, Spring Boot 3.4, PostgreSQL 16
- Maven build, Docker for containerization

### Refined Prompt (v1)
> Goal, Fixed Stack, Entities, Endpoints, Security Rules, Validation Rules, Testing Rules, Conventions, Out of Scope, Acceptance Criteria
```

---

## 4. Data Models (API Contracts)

### 4.1 ChatRequest/ChatResponse (Internal)
```json
{
  "messages": [{"role": "user", "content": "..."}],
  "model": "nvidia/nemotron-3-ultra",
  "temperature": 0.2,
  "maxTokens": 8192,
  "topP": 0.95,
  "systemPrompt": "You are an expert software engineer...",
  "tools": [{"name": "writeFile", "description": "...", "inputSchema": {...}}],
  "metadata": { "taskId": "T-001" }
}
```

### 4.2 Tool Call/Result
```json
// Tool Call
{
  "toolName": "writeFile",
  "arguments": { "path": "src/main/java/.../Book.java", "content": "package ..." },
  "id": "call_abc123"
}

// Tool Result
{
  "toolName": "writeFile",
  "success": true,
  "output": "{\"path\":\"src/main/java/.../Book.java\",\"size\":1234,\"backupPath\":null}",
  "error": null,
  "durationMs": 45
}
```

### 4.3 Finding (Unified)
```json
{
  "fingerprint": "sha256:abc123...",
  "tool": "semgrep",
  "ruleId": "java.sql-injection",
  "severity": "HIGH",
  "file": "src/main/java/.../BookRepository.java",
  "line": 42,
  "message": "SQL injection risk: string concatenation in query",
  "firstSeenRound": 1,
  "fixedInRound": 2,
  "status": "FIXED"
}
```

### 4.4 VerificationResult
```json
{
  "buildOk": true,
  "testsPassed": 24,
  "testsFailed": 0,
  "coverage": 82.5,
  "findings": [
    { "fingerprint": "...", "tool": "semgrep", "severity": "MEDIUM", ... }
  ],
  "costUsd": 0.0234,
  "durationSec": 65
}
```

### 4.5 ProviderHealth
```json
{
  "providers": {
    "nvidia": {
      "status": "UP",
      "circuitBreaker": "CLOSED",
      "totalRequests": 142,
      "successfulRequests": 138,
      "failedRequests": 4,
      "successRate": 0.972,
      "avgLatencyMs": 1240,
      "lastSuccess": "2026-10-26T10:30:00Z",
      "lastFailure": "2026-10-26T10:15:00Z"
    }
  },
  "costTracking": {
    "enabled": true,
    "totalCostUsd": 12.34,
    "dailyCostUsd": 0.52,
    "dailyBudgetUsd": 10.0,
    "dailyBudgetUsedPercent": 5.2
  },
  "overallStatus": "UP",
  "timestamp": "2026-10-26T10:30:00Z"
}
```

---

## 5. Error Codes Registry

| Code | HTTP | Message Template | Recovery Action |
|------|------|------------------|-----------------|
| `JAVA_NOT_FOUND` | 500 | Java {version} not found. Install JDK {version}. | `sdk install java {version}` |
| `MAVEN_NOT_FOUND` | 500 | Maven not found. Install Maven 3.9+. | `sdk install maven` |
| `DOCKER_NOT_RUNNING` | 500 | Docker daemon not accessible. Start Docker Desktop. | `systemctl start docker` |
| `DOCKER_PERMISSION_DENIED` | 403 | Docker permission denied. Add user to docker group. | `sudo usermod -aG docker $USER` |
| `NVIDIA_API_KEY_MISSING` | 401 | NVIDIA_API_KEY not set. Set env var or config. | `companion config set nvidia.api-key <key>` |
| `NVIDIA_API_KEY_INVALID` | 401 | NVIDIA API key invalid or expired. | Regenerate at integrate.api.nvidia.com |
| `NVIDIA_RATE_LIMIT` | 429 | NVIDIA rate limit exceeded. Retry after {retryAfter}s. | Wait + retry with backoff |
| `LLM_TIMEOUT` | 504 | LLM request timed out after {timeout}s. | Increase `--timeout` or check network |
| `LLM_INVALID_RESPONSE` | 502 | LLM returned invalid response: {detail}. | Retry (auto, max 3) |
| `COMPILE_FAILED` | 500 | Compilation failed: {errors}. | Check compiler output |
| `TEST_FAILED` | 500 | {count} tests failed: {list}. | Run `mvn test` locally |
| `SCANNER_FAILED` | 500 | {scanner} failed: {error}. | Check scanner logs |
| `SANDBOX_TIMEOUT` | 504 | Sandbox timed out after {timeout}s. | Increase `--timeout` |
| `SANDBOX_OOM` | 507 | Sandbox OOM killed. Increase `--memory`. | `--memory 4g` |
| `BUDGET_EXCEEDED` | 402 | Budget ${used}/${ceiling} exceeded. | Increase `--budget` |
| `CONFIG_NOT_FOUND` | 404 | Config file not found: {path}. | Run `companion config list` |
| `INVALID_CONFIG` | 400 | Invalid config: {field} = {value}. | Check `companion config list` |
| `PROJECT_NOT_FOUND` | 404 | Project not found: {id}. | Run `companion status` |
| `JOB_NOT_FOUND` | 404 | Job not found: {id}. | Check `companion status` |
| `INVALID_JOB_STATE` | 409 | Job {id} in {state}, cannot {action}. | Wait or cancel |

---

## 6. Configuration Schema

### 6.1 `~/.companion/config.yml`
```yaml
# User-level config (~/.companion/config.yml)
api-key: ""                    # Orchestrator API key (for future web API)
model-router:
  default-provider: nvidia
  providers:
    nvidia:
      api-key: ""              # Set via `companion config set nvidia.api-key`
    openrouter:
      api-key: ""
budget:
  default-per-task-usd: 0.50
  default-per-day-usd: 10.0
  alert-threshold-percent: 80
cli:
  verbose: false
  json-output: false
  auto-approve: false
  max-rounds: 5
  budget-usd: 0.50
```

### 6.2 Project `.companion/config.yml`
```yaml
# Project-level config (<project>/.companion/config.yml)
project:
  name: "library"
  package: "com.example.library"
  build-tool: "maven"
  java-version: "21"
  spring-boot: "3.4.x"
  database: "postgresql"
  auth: "jwt"
  api-style: "rest"
```

---

## 7. Environment Variables

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `NVIDIA_API_KEY` | Yes (v1) | NVIDIA API key | `nvapi-xxx` |
| `OPENROUTER_API_KEY` | No | OpenRouter key | `sk-or-v1-xxx` |
| `OLLAMA_HOST` | No | Ollama endpoint | `http://localhost:11434` |
| `ORCHESTRATOR_API_KEY` | No | Future web API | `orch_sk_xxx` |
| `JWT_SECRET` | No (v1.1+) | JWT signing secret | `openssl rand -base64 64` |
| `GITHUB_TOKEN` | For PR creation | GitHub PAT | `ghp_xxx` |
| `NVD_API_KEY` | Recommended | NVD API for DepCheck | `nvd-xxx` |
| `DOCKER_HOST` | If remote | Docker daemon | `unix:///var/run/docker.sock` |

---

## 8. Versioning & Compatibility

| Artifact Format | Version | Compatibility |
|-----------------|---------|---------------|
| `requirements.md` | v1 | Forward compatible (additive only) |
| `plan.md` | v1 | Forward compatible |
| `findings.json` | v1 | Fingerprint stable across versions |
| `fixes.md` | v1 | Append-only history |
| `report.md` | v1 | Append-only |
| CLI `--json` output | v1 | Additive fields only |

**Breaking Change Policy**: Major version bump required for:
- Field removal/rename in JSON output
- CLI flag removal/rename
- Config key removal/rename
- Artifact file format change

---

## 9. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-10-26 | Initial v1 release (CLI MVP) |
| 1.0.1 | TBD | Bug fixes |
| 1.1.0 | TBD | Multi-provider, JWT auth |

---

*This specification is the contract for v1. All implementation must conform to these contracts.*