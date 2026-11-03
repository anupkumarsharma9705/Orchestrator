---
sidebar_position: 5
title: Offline Mode
---

# Offline Mode

Generate Spring Boot projects **without any server connection** — perfect for air-gapped environments, quick prototyping, or when the server is unavailable.

## What is Offline Mode?

Offline mode bypasses the orchestrator server entirely. The CLI uses local
templates to generate a secure Spring Boot skeleton without any network calls to
the orchestrator server. It does not generate domain code.

### When to Use

| Scenario | Use Offline Mode? |
|----------|-------------------|
| Air-gapped / no internet | ✅ Yes |
| Server down / unreachable | ✅ Yes |
| Quick prototyping | ✅ Yes |
| CI/CD pipelines (no server) | ✅ Yes |
| Full feature set (clarification, auto-fix) | ❌ No — use online mode |

## Usage

```bash
# Basic offline generation
companion generate "REST CRUD for Book entity with author, genre, ISBN" --name library --offline

# With options
companion generate "REST API for User management with JWT auth" \
  --name user-service \
  --offline \
  --base-package com.mycompany.users \
  --output-format json \
  --dry-run
```

## Flags

| Flag | Description |
|------|-------------|
| `--offline` | Enable offline mode (no server connection) |
| `--dry-run` | Show what would be generated without creating files |
| `--base-package` | Base Java package (default: `com.example.<project>`) |
| `--output-format` | Output format: `table`, `json`, `markdown` |

## What Works Offline

| Feature | Online | Offline |
|---------|--------|---------|
| Template-based Spring Boot skeleton | ✅ | ✅ |
| JWT security configuration and error handling | ✅ | ✅ |
| Build tooling, Checkstyle, SpotBugs, Docker, CI | ✅ | ✅ |
| Entity/Repository/Service/Controller generation | ✅ | ❌ |
| DTOs, validation, pagination | ✅ | ❌ |
| Generated tests | ✅ | ❌ |
| Clarification questions | ✅ | ❌ |
| LLM-powered plan generation | ✅ | ❌ |
| Auto-fix loop | ✅ | ❌ |
| Security scanning | ✅ | ❌ |
| Cost tracking | ✅ | ❌ |

## How It Works

Offline mode uses the **TemplateEngine** to render a pre-built Spring Boot
skeleton with your project name and package:

```
Your description
       │
       ▼
┌─────────────────────────────────────┐
│     TemplateEngine (local)          │
│  • Applies project name/package     │
│  • Renders security + config files  │
│  • Renders build/CI/Docker files    │
│  • Does not generate domain code    │
└─────────────────────────────────────┘
       │
       ▼
   Project files
```

The template includes:
- Spring Boot entry point
- JWT security configuration and authentication filter
- Global exception handler
- `application.yml` with secure defaults
- `pom.xml` with Checkstyle, SpotBugs/FindSecBugs, and JaCoCo
- Checkstyle and SpotBugs configuration
- Dockerfile and Docker Compose files
- GitHub Actions CI workflow
- Project README

## Examples

### Basic REST CRUD
```bash
companion generate "REST CRUD for Book entity with title, author, ISBN, published date" --name library --offline
```

### With Relationships
```bash
companion generate "Blog platform: User has many Posts, Post has many Comments, User has Profile" --name blog --offline
```

### With Custom Package
```bash
companion generate "E-commerce: Product, Category, Order, Cart" --name shop --offline --base-package com.mycompany.shop
```

### Dry Run (Preview)
```bash
companion generate "Task manager with User, Project, Task" --name taskman --offline --dry-run --output-format json
```

### Output Formats

```bash
# Table (default)
companion generate "..." --name demo --offline

# JSON for scripting
companion generate "..." --name demo --offline --output-format json

# Markdown for documentation
companion generate "..." --name demo --offline --output-format markdown
```

## Generated Project Structure

```
my-project/
├── src/main/java/com/example/project/
│   ├── ProjectApplication.java
│   ├── config/SecurityConfig.java
│   ├── security/JwtAuthenticationFilter.java
│   ├── exception/GlobalExceptionHandler.java
│   └── {controller,domain,dto,mapper,repository,service}/package-info.java
├── src/main/resources/application.yml
├── src/test/java/              # add tests here
├── checkstyle.xml
├── spotbugs-exclude.xml
├── Dockerfile
├── docker-compose.yml
├── .github/workflows/ci.yml
├── pom.xml
└── README.md
```

## Limitations

| Limitation | Workaround |
|------------|------------|
| No domain code generation | Add entities, repositories, services, controllers, and tests yourself |
| No LLM-powered planning | Use online mode for complex projects |
| No auto-fix loop | Run `mvn verify` locally, fix manually |
| No clarification questions | Use a descriptive project name and `--base-package` |
| No cost tracking | N/A offline |
| Template-based only | Use online for custom logic |

## Best Practices

### Choose a Clear Name and Package
```bash
companion generate "Book API" --name library --base-package com.example.library --offline
```

### Add Domain Code After Generation
Offline mode intentionally generates a skeleton. Add your entities,
repositories, services, controllers, and tests, then run:

```bash
mvn verify
```

### Use Dry-Run First
```bash
companion generate "..." --name demo --offline --dry-run --output-format json
```

## Next Steps

- [Quick Start](/docs/quick-start) — Generate your first project
- [Configuration](/docs/configuration) — Server, API keys, defaults
- [CLI Reference](/docs/cli-reference) — All commands and flags