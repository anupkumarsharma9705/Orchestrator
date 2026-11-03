---
sidebar_position: 9
title: Contributing
---

# Contributing

Welcome! We're excited you want to contribute to Java Companion Orchestrator.

## Quick Links

- **Code of Conduct:** [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md)
- **Security Policy:** [SECURITY.md](../SECURITY.md)
- **Issue tracker and discussions:** use the repository that hosts your clone.

## Development Setup

### Prerequisites

- **Java 21+** (OpenJDK recommended)
- **Maven 3.9+**
- **Docker 27+** (for sandbox tests)
- **Node.js 18+** (for docs)
- **Git**

### Clone & Build

```bash
# Clone the repository that hosts this project
git clone <repository-url> orchestrator
cd orchestrator

# Build all modules
mvn -B -ntp clean install -DskipTests

# Run tests
mvn -B -ntp test
```

### IDE Setup

**IntelliJ IDEA (Recommended):**
1. Import as Maven project
2. Enable annotation processing
3. Set Java 21 SDK
4. Run `CompanionCli` main class

**VS Code:**
1. Install Extension Pack for Java
2. Install Maven for Java
3. Open folder, let it auto-configure

## Project Structure

```
orchestrator/
├── app-cli/                 # CLI application (picocli + Spring Boot)
├── orchestrator-server/     # REST API server
├── orchestrator-api-contract/ # Shared API contract definitions
├── engine-core/             # Generation pipeline (Clarifier, Planner, Coder, Fixer, Refiner)
├── engine-llm/              # LLM providers, routing, cost tracking
├── engine-model/            # Shared DTOs, entities, enums
├── engine-templates/        # TemplateEngine (Spring Boot generation)
├── engine-sandbox/          # Docker sandbox execution
├── engine-verify/           # Verification runners (scanners, build)
├── engine-store/            # Persistence (PostgreSQL)
├── eval/                    # Evaluation framework
├── docs/                    # Documentation (Docusaurus)
└── distribution/            # Packaging templates (not published)
```

## Code Style

### Java

- **Imports:** Sorted, no wildcards
- **Naming:** Standard Java conventions
- **Documentation:** Javadoc for public APIs
- **Verification:** `mvn -B -ntp clean verify` must pass

There is no automatic formatter plugin configured. Match the surrounding style
when editing.

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat` — New feature
- `fix` — Bug fix
- `docs` — Documentation
- `style` — Formatting
- `refactor` — Code restructuring
- `test` — Tests
- `chore` — Maintenance

**Examples:**
```
feat(cli): add self-update command
fix(sandbox): handle OOM in container
docs(readme): add installation instructions
test(coder): add unit tests for file tools
```

### Branch Naming

```
<type>/<short-description>
```

Examples:
```
feat/self-update-command
fix/sandbox-oom-handling
docs/update-install-guide
```

## Testing

### Run All Tests

```bash
# All modules
mvn test

# Single module
mvn test -pl engine-core
mvn test -pl app-cli

# Specific test
mvn test -pl engine-core -Dtest=ClarifierTest#testClarificationSession
```

### Test Categories

| Category | Command | Purpose |
|----------|---------|---------|
| Unit | `mvn test` | Fast, isolated tests |
| Full build | `mvn -B -ntp clean verify` | Compile, tests, packaging |
| Single test | `mvn test -pl app-cli -Dtest=CliIntegrationTest` | Focused iteration |

Tests that need a Docker daemon are skipped or degrade when Docker is
unavailable; check the module's test output before assuming full coverage.

### Writing Tests

```java
// Unit test example
@Test
void testClarificationGeneratesQuestions() {
    var clarifier = new Clarifier();
    var session = clarifier.start("REST API for Books");
    assertThat(session.getQuestions()).hasSizeGreaterThan(0);
}

// Integration test example
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {
    @Autowired MockMvc mvc;
    
    @Test
    void submitTask_returnsTaskId() throws Exception {
        mvc.perform(post("/api/tasks").contentType(APPLICATION_JSON)
            .content("{\"projectId\":\"test\",\"request\":\"...\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").exists());
    }
}
```

## Pull Request Process

### Before Submitting

1. **Run checks:**
   ```bash
   mvn -B -ntp clean verify
   ```

2. **Update docs** if behavior changes

3. **Add tests** for new features

4. **Update CHANGELOG.md** (if applicable)

### PR Template

```markdown
## Description
Brief description of changes

## Type
- [ ] Feature
- [ ] Bug fix
- [ ] Documentation
- [ ] Refactor
- [ ] Test

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing done

## Checklist
- [ ] `mvn -B -ntp clean verify` passes
- [ ] Tests added/updated
- [ ] Docs updated
- [ ] No breaking changes (or noted)
```

### Review Process

1. **Automated checks** must pass (CI)
2. **Code review** by maintainer
3. **Merge** after approval

## Code Review Guidelines

### For Authors

- Keep PRs small (< 400 lines)
- Write clear commit messages
- Add tests for new behavior
- Update documentation

### For Reviewers

- Check logic, not just style
- Verify tests cover new code
- Ensure no security regressions
- Confirm documentation updated

## Architecture Decisions

There is no ADR directory today. If you make a significant architectural
decision, record it in the pull request description using this structure:

```markdown
# ADR: Title

## Status
Proposed / Accepted / Superseded

## Context
What problem are we solving?

## Decision
What did we decide?

## Consequences
What are the trade-offs?
```

## Release Process

### Versioning

[Semantic Versioning](https://semver.org/):
- `MAJOR.MINOR.PATCH`
- `0.x.x` — Pre-1.0, breaking changes allowed

### Release Checklist

```bash
# 1. Update version
mvn versions:set -DnewVersion=0.2.0

# 2. Update CHANGELOG.md

# 3. Run full test suite
mvn clean verify

# 4. Tag release
git tag v0.2.0
git push origin v0.2.0

# 5. GitHub Actions builds & releases automatically
```

## Community

### Communication Channels

- **Issue tracker:** Bugs and feature requests
- **Pull requests:** Code and documentation changes
- **Security reports:** private GitHub Security Advisories (see SECURITY.md)

### Recognition

Contributors are recognized in release notes and the repository's contributor
graph.

---

**Thank you for contributing!** 🎉

Every contribution — code, docs, bug reports, ideas — makes Companion better for everyone.