# System Architecture
## Java Companion Orchestrator — v1 CLI MVP

---

## 1. Architectural Overview

### 1.1 System Context
```text
┌─────────────────────────────────────────────────────────────────┐
│                        User / CI/CD                                │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        companion.jar (CLI)                       │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────┐  │
│  │   CLI       │  │     ENGINE       │  │      TOOLS         │  │
│  │  (picocli)  │  │   (Library)      │  │    (20+)           │  │
│  └──────┬──────┘  └────────┬─────────┘  └────────┬───────────┘  │
│         │                  │                      │              │
│  ┌──────┴──────┐   ┌───────┴────────┐   ┌────────┴────────┐   │
│  │  Clarifier  │   │  ModelRouter   │   │  File/Build/    │   │
│  │  Refiner    │   │  (Providers)   │   │  Scan/Fix Tools │   │
│  │  Planner    │   │  CostTracker   │   │  Verifier       │   │
│  │  Coder      │   │  CircuitBrkr   │   │  Sandbox        │   │
│  │  Fixer      │   │                │   │                 │   │
│  └─────────────┘   └────────────────┘   └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
            ┌───────────────┐   ┌───────────────┐
            │  SQLite DB    │   │  Sandbox      │
            │  (Jobs,       │   │  (Docker)     │
            │   Rounds,     │   │  Build/Test/  │
            │   Findings)   │   │  Scan/Fix     │
            └───────────────┘   └───────────────┘
```

### 1.2 Architectural Style
- **Modular Monolith**: Single deployable JAR (`companion.jar`) with clear module boundaries
- **Plugin Architecture**: Tools, providers, verifiers registered via Spring `@Component`
- **Modular Maven**: 7 engine modules + 1 CLI app module
- **Configuration-Driven**: All behavior via `application.yml` + profiles (`test`, `prod`)

---

## 2. Module Architecture

### 2.1 Module Dependency Graph
```text
┌─────────────────┐
│   app-cli       │  ← Entry point (picocli)
└────────┬────────┘
         │
┌────────▼────────┐
│  engine-core    │  ← Orchestrator, Clarifier, Refiner, Planner, Coder, Fixer
└────────┬────────┘
         │
    ┌────┴────┬─────────┬──────────┬────────────┐
    ▼         ▼         ▼          ▼            ▼
┌─────────┐ ┌──────┐ ┌────────┐ ┌────────┐ ┌──────────┐
│engine-  │ │engine│ │engine- │ │engine- │ │engine-   │
│model    │ │-llm  │ │verify  │ │sandbox │ │store     │
└─────────┘ └──────┘ └────────┘ └────────┘ └──────────┘
                                    │
                                    ▼
                            ┌──────────────┐
                            │engine-templates│
                            └──────────────┘
```

### 2.2 Module Responsibilities

| Module | Responsibility | Key Classes |
|--------|----------------|-------------|
| **engine-model** | Pure data records (no dependencies) | `Requirement`, `RequirementsDoc`, `PlanTask`, `Plan`, `Finding`, `Fix`, `Job`, `Severity`, `Status`, `FingerprintUtil` |
| **engine-llm** | LLM abstraction: routing, failover, cost, profiles | `ModelRouter`, `ModelProvider`, `NvidiaProvider`, `CostTracker`, `CircuitBreaker`, `ModelProfileConfig` |
| **engine-core** | Business logic: Clarifier, Refiner, Planner, Coder, Fixer, Orchestrator | `Clarifier`, `Refiner`, `Planner`, `Coder`, `Fixer`, `Orchestrator`, `SecurityEnforcer` |
| **engine-verify** | Verification pipeline: 7 scanners, parsers, Finding model | `Verifier`, `*ScannerTool`, `FindingParser`, `VerificationResult` |
| **engine-sandbox** | Docker runner: limits, non-root, resource caps | `SandboxRunner`, `SandboxConfig`, `SandboxResult` |
| **engine-store** | Artifact persistence: files + SQLite, Flyway migrations | `ArtifactStore`, `*Repository`, `FlywayConfig`, `ArtifactWriter` |
| **engine-templates** | Spring Boot skeleton + `rules.yml` (security rules) | File-based assets |
| **app-cli** | CLI entry point (picocli), commands, terminal UX | `OrchestratorCli`, `*Command`, `StreamingOutputHandler` |

---

## 3. Core Data Flow

### 3.1 Generate Command Flow
```text
User Request (String)
        │
        ▼
┌──────────────────────────────────────────────────────────────┐
│  Orchestrator.submitTask(projectId, request, requester)      │
└────────────────────────────┬─────────────────────────────────┘
                             │
                             ▼
     ┌─────────────────────────────────────────┐
     │ 1. Load Project + Memory                │
     │    ProjectManager.get(projectId)        │
     │    MemoryService.loadMemory(project)    │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 2. Context Selection                    │
     │    ContextManager.selectContext()       │
     │    → TF-IDF scoring + file filtering    │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 3. Requirements Compilation             │
     │    RequirementCompiler.compile()        │
     │    → PLANNER LLM → RequirementsDoc      │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 4. Task Planning                        │
     │    TaskPlanner.plan()                   │
     │    → PLANNER LLM → TaskPlan             │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 5. Approval Gate                        │
     │    approvalService.requestApproval()    │
     │    → waitForApproval(timeout)           │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 6. Execution (5-Round Loop)             │
     │    For each task:                       │
     │      • Build coder prompt               │
     │      • ModelRouter.chatWithProfile()    │
     │      • ToolCallParser.parse()           │
     │      • ToolRegistry.execute()           │
     │      • SecurityEnforcer.validate()      │
     │      • AuditService.log()               │
     │      • Verifier.run() → Findings        │
     │      • Fixer loop (rounds 2-5)          │
     └────────────────────────────┬────────────┘
                                  │
                                  ▼
     ┌─────────────────────────────────────────┐
     │ 7. Completion & Documentation           │
     │    AutoDocumentationService             │
     │    LearningExtractor                    │
     │    CostTracker finalize                 │
     └─────────────────────────────────────────┘
```

### 3.2 Key Data Structures Flow
```text
User Request (String)
        │
        ▼
RequirementsDoc (11 categories + assumptions + refinedPrompt)
        │
        ▼
Plan (Task[] with id, files[], dependsOn[], requirementIds[])
        │
        ▼
Task (id, title, files[], dependsOn[], requirementIds[], acceptance[])
        │
        ▼
ChatRequest (messages[], model, temperature, tools, systemPrompt, metadata{taskId})
        │
        ▼
ChatResponse (choices[Choice], usage, model, provider)
        │
        ▼
ToolCall (toolName, arguments, id)
        │
        ▼
ToolResult (toolName, success, output, error, durationMs)
        │
        ▼
Finding (fingerprint, tool, ruleId, severity, file, line, message, round)
        │
        ▼
Fix (findingFingerprint, round, summary, filesChanged, result)
```

---

## 4. Module Deep Dives

### 4.1 engine-model (Pure Data Records)
```java
// Core records - no dependencies, JSON-serializable
record Requirement(String id, String category, String text, Priority priority)
record RequirementsDoc(String projectName, String version, List<Requirement> items, String refinedPrompt)
record PlanTask(String id, String title, String description, List<String> files, 
                List<String> dependsOn, List<String> requirementIds, List<String> acceptance)
record Plan(String projectName, String version, List<PlanTask> tasks)
record Finding(String fingerprint, String tool, String ruleId, Severity severity,
               String file, int line, String message, int firstSeenRound, 
               Integer fixedInRound, Status status)
record Fix(String findingFingerprint, int round, String summary, 
           List<String> filesChanged, Result result)
```

### 4.2 engine-llm (LLM Abstraction Layer)
```java
// Provider abstraction
interface ModelProvider {
    String getProviderName();
    boolean isAvailable();
    boolean supportsStreaming();
    Mono<ChatResponse> chat(ChatRequest request);
    ModelCapabilities getCapabilities();
}

// Provider implementations:
// - NvidiaProvider (primary v1)
// - OpenRouterProvider (v1.1+)
// - OllamaProvider (local fallback)
// - TestModelProvider (tests)

// ModelRouter: failover + circuit breaker + cost tracking
@Service
class ModelRouter {
    private final List<ModelProvider> providers;
    private final CostTracker costTracker;
    private final Map<String, CircuitBreaker> circuitBreakers;
    
    Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile) {
        // 1. Try primary provider from profile
        // 2. Fallback chain: profile.fallbackProfiles → defaultProfile
        // 3. Last resort: any available provider
        // Circuit breaker: 3 failures → 60s cooldown
        // Cost tracking: tokens → $ via price table
    }
}
```

### 4.3 engine-core (Orchestration Logic)
```java
// Clarifier: vague request → requirements
@Service
class Clarifier {
    RequirementsDoc clarify(String request) {
        // 1. Build prompt with checklist (entities, roles, auth, DB, etc.)
        // 2. Call PLANNER model
        // 3. Parse JSON questions (≤8)
        // 4. Return questions to client
        // 5. On answers: produce RequirementsDoc v1
    }
}

// Refiner: RequirementsDoc → structured build prompt
@Service
class Refiner {
    String refine(RequirementsDoc req) {
        // Template sections: Goal, Fixed Stack, Entities, Endpoints, 
        // Security Rules, Validation Rules, Testing Rules, Conventions, 
        // Out of Scope, Acceptance Criteria
    }
}

// Planner: RequirementsDoc + RefinedPrompt → TaskPlan
@Service
class Planner {
    TaskPlan plan(RequirementsDoc req, String refinedPrompt) {
        // 1. Prompt PLANNER model with req + refinedPrompt
        // 2. Parse JSON → TaskPlan
        // 3. Validate: no cycles, all req IDs covered, topological sort
    }
}

// Coder: TaskPlan → code via tool calls
@Service
class Coder {
    List<ToolResult> execute(TaskPlan plan, Project project) {
        for (Task task : plan.tasks()) {
            // 1. Build coder prompt (task + plan excerpts + files + conventions + security rules)
            // 2. ModelRouter.chatWithProfile(CODER) → tool calls
            // 3. Parse tool calls → execute via ToolRegistry
            // 4. SecurityEnforcer.validate()
            // 5. Compile in sandbox → retry 3x on fail
        }
    }
}

// Fixer: Findings + file content + task → edits
@Service
class Fixer {
    List<ToolResult> fix(List<Finding> findings, Project project, TaskPlan plan) {
        // 1. Group OPEN findings by file (CRITICAL first)
        // 2. Per group: Fixer LLM → edits via staging → promote → git commit
        // 3. Full Verifier re-run
        // 4. Fingerprint diff: Gone→FIXED, Still→OPEN, New→NEW
        // 5. Regression guard: new > fixed → revert + retry once
    }
}
```

### 4.4 engine-verify (Verification Pipeline)
```java
@Service
class Verifier {
    VerificationResult verify(Project project) {
        // Parallel execution of all scanners:
        var results = parallel(
            () -> compileCheck(),
            () -> runTests(),
            () -> runCoverage(),
            () -> runSpotBugs(),
            () -> runCheckstyle(),
            () -> runSemgrep(),
            () -> runDepCheck(),
            () -> runTrivy(),
            () -> runSecretScan()
        );
        
        // Parse each tool output → List<Finding>
        // Compute fingerprint: SHA256(tool + "|" + ruleId + "|" + filePath + "|" + codeLine)
        // Return VerificationResult { buildOk, testsPassed, testsFailed, coverage, findings[] }
    }
}
```

### 4.5 engine-sandbox (Docker Runner)
```java
@Component
class SandboxRunner {
    ToolResult run(String command, Path workspace, Duration timeout) {
        // Docker run with:
        // --memory=2g --cpus=2 --pids-limit=100
        // --user=1000:1000 --read-only --tmpfs /tmp
        // -v workspace:/workspace:rw
        // --network=none (v1)
        // timeout → kill container
    }
}
```

### 4.6 engine-store (Persistence)
```java
// SQLite + Flyway
// Tables: jobs, rounds, findings, fixes, costs, approvals, project_memory

@Repository
interface TaskCostRepository extends JpaRepository<TaskCostEntity, String> {
    List<TaskCostEntity> findByTaskId(String taskId);
}

@Repository
interface ApprovalRepository extends JpaRepository<ApprovalEntity, String> {
    Optional<ApprovalEntity> findByTaskId(String taskId);
}
```

### 4.7 app-cli (CLI Entry Point)
```java
@SpringBootApplication
@Picocli
class OrchestratorCli {
    // Commands: generate, status, approve, verify, fix, report, config, doctor
    // Terminal UX: progress line, questions with defaults, flags (--yes, --json, --offline)
    // Config file (~/.companion/config.yml) + environment variables
}
```

---

## 5. Infrastructure & Deployment

### 5.1 Deployment Architecture (v1)
```text
┌─────────────────────────────────────────────────────────────┐
│                    User Machine                                │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────────┐  │
│  │ companion.jar │    │   Docker     │    │   SQLite       │  │
│  │  (CLI)        │◄──▶│  (Sandbox)   │◄──▶│   (orchestrator.db)│
│  └─────────────┘    └──────────────┘    └────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```
- **Single JAR**: `companion.jar` (executable, includes all engine modules)
- **Docker**: Required for sandbox (build/test/scan)
- **Database**: SQLite file (`orchestrator.db`) in project root or `~/.companion/`
- **Config**: `~/.companion/config.yml` + environment variables

### 5.2 CI/CD Pipeline
```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4 (java-version: 21)
      - run: ./mvnw verify
      - run: ./mvnw test -Dtest=*IntegrationTest
      - uses: docker/build-push-action (sandbox image)
```

### 5.3 Configuration
```yaml
# application.yml (base)
spring:
  datasource:
    url: jdbc:h2:mem:orchestrator
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

orchestrator:
  model-router:
    default-provider: nvidia
    timeout-seconds: 60
    circuit-breaker:
      enabled: true
      failure-threshold: 5
    cost-tracking:
      enabled: true
      budget-per-task-usd: 0.50
      budget-per-day-usd: 10.0
    providers:
      nvidia:
        enabled: true
        base-url: https://integrate.api.nvidia.com/v1
        models:
          planner: nvidia/nemotron-3-ultra
          coder: nvidia/nemotron-3-ultra
          ...
    profiles:
      planner: { provider: nvidia, temperature: 0.1, ... }
      coder: { provider: nvidia, temperature: 0.2, ... }
      ...
```

---

## 6. Security Architecture

### 6.1 Threat Model
| Threat | Mitigation |
|--------|------------|
| Prompt Injection | Strip instruction-like text from project files before sending to LLM |
| Secret Leakage | Scan prompts for key patterns; never log secrets; env vars only |
| Sandbox Escape | Docker: non-root, read-only rootfs, no host mounts, resource caps |
| Path Traversal | Path resolution: `basePath.resolve(path).normalize()` + `startsWith(basePath)` + `toRealPath()` |
| Command Injection | No shell execution; `ProcessBuilder` with explicit args |
| Secret Storage | Env vars + `~/.companion/keys/` (chmod 600); never in code/logs |

### 6.2 Security Rules (Enforced by Verifier)
| ID | Rule | Tool | Severity |
|----|------|------|----------|
| SEC-1 | Constructor injection only | ArchUnit | HIGH |
| SEC-2 | No SQL concatenation | Semgrep | HIGH |
| SEC-3 | BCrypt/Argon2 for passwords | SpotBugs | HIGH |
| SEC-4 | SecureRandom for tokens | Semgrep | HIGH |
| SEC-5 | No MD5/SHA1/DES/RC4/ECB | SpotBugs/Semgrep | HIGH |
| SEC-6 | @Valid on @RequestBody | ArchUnit | HIGH |
| SEC-7 | No secrets in code/logs | SecretScan | HIGH |
| SEC-8 | CORS allow-list only | Semgrep | HIGH |
| SEC-9 | Rate limiting on auth | Semgrep | HIGH |
| SEC-10 | Actuator health only public | ArchUnit | HIGH |

### 6.3 Observability
```yaml
logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  level:
    com.example.orchestrator: DEBUG
    org.springframework: WARN

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

**Key Metrics:**
- `orchestrator.jobs.started/completed/failed` (counter)
- `orchestrator.llm.tokens` (summary: prompt/completion/total)
- `orchestrator.llm.cost` (summary: USD)
- `orchestrator.verifier.findings` (timer + counter by severity)
- `orchestrator.sandbox.duration` (timer)

**Health Checks:** `GET /actuator/health` → provider status, circuit breaker state, cost tracking

---

## 7. Data Models (Database Schema)

### 7.1 Flyway Migrations
```text
V1__initial_schema.sql      -- jobs, rounds, findings, fixes, costs, projects
V2__cost_tracking.sql       -- task_costs, daily_costs, cost_aggregates
V3__approval_tracking.sql   -- approvals, approval_audit
V4__multi_workspace.sql     -- workspaces, project.workspace_id (v2+)
```

### 7.2 Key Tables
```sql
-- jobs
CREATE TABLE jobs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL,  -- CREATED, CLARIFYING, REQUIREMENTS_READY, PLANNED, AWAITING_APPROVAL, BUILDING, VERIFYING, FIXING, COMPLETED, FAILED, CANCELLED
    round INT DEFAULT 1,
    budget_usd DECIMAL(10,6),
    cost_usd DECIMAL(10,6) DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

-- findings
CREATE TABLE findings (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    tool VARCHAR(64) NOT NULL,
    rule_id VARCHAR(128),
    severity VARCHAR(16) NOT NULL,  -- INFO, LOW, MEDIUM, HIGH, CRITICAL
    file VARCHAR(512),
    line INT,
    message TEXT,
    first_seen_round INT NOT NULL,
    fixed_in_round INT,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN, FIXED, REGRESSED, ACCEPTED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 8. API Contracts (Internal - Engine Interfaces)

### 8.1 ModelRouter
```java
interface ModelRouter {
    Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile);
    Mono<ChatResponse> chat(ChatRequest request);  // default profile
    List<String> getAvailableProviders();
    Map<String, CircuitBreaker.State> getCircuitBreakerStates();
    CostTracker getCostTracker();
}
```

### 8.2 ToolRegistry
```java
interface ToolRegistry {
    ToolResult execute(Project project, String toolName, String jsonArgs, PermissionContext ctx);
    Map<String, ToolSpec> getAllSpecs();
}
```

### 8.3 Common Tools
| Tool | Input Schema | Output |
|------|--------------|--------|
| `writeFile` | `{path, content}` | `{path, size, backupPath}` |
| `editFile` | `{path, oldText, newText}` | `{path, replacements, backupPath}` |
| `readFile` | `{path}` | `{path, content}` |
| `listDirectory` | `{path, maxDepth}` | `{files[], dirs[]}` |
| `searchFiles` | `{pattern, contentPattern, path, maxResults}` | `{matches[]}` |
| `runMavenBuild` | `{path, goals[], timeout, skipTests}` | `{exitCode, output, success}` |
| `runMavenTest` | `{path, goals[], timeout}` | `{exitCode, output, success}` |
| `semgrepScan` | `{path, config, timeout}` | `{exitCode, output, findings[]}` |
| `trivyScan` | `{path, target, timeout}` | `{exitCode, output, vulnerabilities[]}` |
| `dependencyCheck` | `{path, timeout}` | `{exitCode, output, vulnerabilities[]}` |
| `secretScan` | `{path, timeout}` | `{exitCode, output, findings[]}` |

---

## 9. Configuration Reference

### 9.1 application.yml (Complete)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orchestrator
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

orchestrator:
  model-router:
    default-provider: nvidia
    timeout-seconds: 60
    circuit-breaker:
      enabled: true
      failure-threshold: 5
      timeout-seconds: 30
    cost-tracking:
      enabled: true
      budget-per-task-usd: 0.50
      budget-per-day-usd: 10.0
    providers:
      nvidia:
        enabled: true
        base-url: https://integrate.api.nvidia.com/v1
        models:
          planner: nvidia/nemotron-3-ultra
          coder: nvidia/nemotron-3-ultra
          ...
    profiles:
      planner: { provider: nvidia, temperature: 0.1, ... }
      coder: { provider: nvidia, temperature: 0.2, ... }
      reviewer: { provider: nvidia, temperature: 0.1, ... }
      ...
```

---

## 10. Testing Architecture

### 10.1 Test Pyramid
```text
                    ┌─────────────┐
                    │   E2E (5)   │  ← Golden specs, real NVIDIA API
                    ├─────────────┤
                    │ Integration │  ← DB, sandbox, external tools
                    ├─────────────┤
                    │   Unit      │  ← 70%+ coverage, FakeModel, FakeProvider
                    └─────────────┘
```

### 10.2 Test Organization
```text
src/test/java/com/example/orchestrator/
├── context/ContextManagerTest.java
├── integration/
│   ├── ExecutionIntegrationTest.java
│   └── NvidiaE2ETest.java
├── memory/ProjectMemoryServiceTest.java
├── model/router/ToolCallParserTest.java
├── tool/impl/
│   ├── DependencyCheckToolTest.java
│   ├── DockerBuildToolTest.java
│   ├── SemgrepScanToolTest.java
│   └── ...
├── verification/VerificationControllerTest.java
└── config/
    ├── TestModelProviderConfig.java
    └── TestModelRouterConfig.java
```

### 10.3 Test Configuration
```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop

orchestrator:
  model-router:
    default-provider: test
    providers:
      test:
        enabled: true
      nvidia:
        enabled: false
```

---

## 11. Deployment & Operations

### 11.1 Build & Package
```bash
# Build executable JAR
./mvnw clean package -DskipTests

# Run
java -jar target/companion-0.0.1-SNAPSHOT.jar generate "REST CRUD for Book" --name library
```

### 11.2 Docker (Sandbox Builder Image)
```dockerfile
# docker/sandbox.Dockerfile
FROM eclipse-temurin:21-jdk
RUN apt-get update && apt-get install -y maven docker.io
# Install scanners: semgrep, trivy, dependency-check, spotbugs, gitleaks
ENTRYPOINT ["java", "-jar", "/app/companion.jar"]
```

### 11.3 Production Deployment (v1)
```bash
# Single VPS with Docker Compose
# services: api, worker, postgres, web-ui (static), nginx
# Reverse proxy with auto HTTPS
```

---

*This architecture document is a living document. Update with each architectural decision.*