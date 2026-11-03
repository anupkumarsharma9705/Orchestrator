# Architecture Document
## Java Companion Orchestrator - v1 (CLI MVP)

---

## 1. Architectural Overview

### 1.1 System Context
```
┌─────────────────────────────────────────────────────────────────┐
│                        User / CI/CD                               │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      companion.jar (CLI)                        │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────┐  │
│  │   CLI       │  │     ENGINE       │  │      TOOLS         │  │
│  │  (picocli)  │  │   (Library)      │  │    (20+)           │  │
│  └──────┬──────┘  └────────┬─────────┘  └────────┬────────────┘  │
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
```
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
```

### 2.2 Module Responsibilities

| Module | Responsibility | Key Classes |
|--------|----------------|-------------|
| **engine-model** | Pure data records (no dependencies) | `Requirement`, `PlanTask`, `Finding`, `Fix`, `Job`, `CostTracker` |
| **engine-llm** | LLM abstraction: routing, failover, cost, profiles | `ModelRouter`, `ModelProvider`, `CostTracker`, `ProviderHealthController` |
| **engine-core** | Business logic: Clarifier, Refiner, Planner, Coder, Fixer, Orchestrator | `Clarifier`, `Refiner`, `Planner`, `Coder`, `Fixer`, `Orchestrator` |
| **engine-verify** | Verification pipeline: 7 scanners, parsers, Finding model | `Verifier`, `*ScannerTool`, `FindingParser` |
| **engine-sandbox** | Docker runner: limits, non-root, resource caps | `SandboxRunner`, `SandboxConfig` |
| **engine-store** | Artifact persistence: files + SQLite, Flyway migrations | `ArtifactStore`, `*Repository`, `FlywayConfig` |
| **engine-templates** | Spring Boot skeleton + `rules.yml` (security rules) | File-based assets |
| **app-cli** | CLI entry point (picocli), commands, terminal UX | `OrchestratorCli`, `*Command`, `StreamingOutputHandler` |

---

## 3. Core Data Flow

### 3.1 Generate Command Flow
```
User Request
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
```
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
record RequirementDoc(String projectName, String version, List<Requirement> items, String refinedPrompt)
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
    Mono<String> chatStreaming(ChatRequest request);
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
            // 4. Parse tool calls → execute via ToolRegistry
            // 5. SecurityEnforcer.validate()
            // 6. Compile in sandbox → retry 3x on fail
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

### 4.3 engine-sandbox (Docker Runner)
```java
@Component
class SandboxRunner {
    ToolResult run(String command, Path workspace, Duration timeout) {
        // Docker run with:
        // --memory=2g --cpus=2 --pids-limit=100
        // --user=1000:1000 --read-only --tmpfs /tmp
        // -v workspace:/workspace:rw
        // --network=none (v1) / --network=host (later)
        // timeout → kill container
    }
}
```

### 4.5 engine-store (Persistence)
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

---

## 5. Infrastructure & Deployment

### 5.1 Deployment Architecture (v1)
```
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
orchestrator:
  model-router:
    default-provider: nvidia
    timeout-seconds: 60
    max-retries: 3
    circuit-breaker:
      enabled: true
      failure-threshold: 3
      timeout-seconds: 60
    cost-tracking:
      enabled: true
      budget-per-task-usd: 0.50
      budget-per-day-usd: 10.0
      alert-threshold-percent: 80.0
    providers:
      nvidia:
        enabled: true
        base-url: https://integrate.api.nvidia.com/v1
        api-key: ${NVIDIA_API_KEY:}
        models: { planner: nvidia/nemotron-3-ultra, ... }
      openrouter: { enabled: false }
      ollama: { enabled: false }
      kaggle: { enabled: false }

# application-prod.yml (overrides)
spring:
  datasource:
    url: jdbc:sqlite:orchestrator.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.community.dialect.SQLiteDialect

# application-test.yml (test profile)
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
```

---

## 6. Security Architecture

### 6.1 Threat Model
| Threat | Mitigation |
|--------|------------|
| **Prompt Injection** | Strip instruction-like text from project files before sending to LLM |
| **Secret Leakage** | Scan prompts for key patterns; never log secrets; env vars only |
| **Sandbox Escape** | Docker: non-root, read-only rootfs, no host mounts, resource caps |
| **Path Traversal** | Path resolution: `basePath.resolve(path).normalize()` + `startsWith(basePath)` + `toRealPath()` |
| **Command Injection** | No shell execution; `ProcessBuilder` with explicit args |
| **Secret Storage** | Env vars + `~/.companion/keys/` (chmod 600); never in code/logs |

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

---

## 6. Observability

### 6.1 Logging
```yaml
logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  level:
    com.example.orchestrator: DEBUG
    org.springframework: WARN
```
- **Correlation ID**: MDC `jobId` in every log line
- **Structured JSON** in prod: Logstash encoder
- **Audit log**: Separate file `audit.log` (JSON lines)

### 6.2 Metrics (Micrometer + Actuator)
```yaml
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
**Key Metrics**:
- `orchestrator.jobs.started` (counter)
- `orchestrator.jobs.completed` (counter)
- `orchestrator.jobs.failed` (counter)
- `orchestrator.llm.tokens` (summary: prompt/completion/total)
- `orchestrator.llm.cost` (summary: USD)
- `orchestrator.verifier.findings` (timer + counter by severity)
- `orchestrator.sandbox.duration` (timer)

### 6.3 Health Checks
```java
@Component
class ProviderHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        var providers = modelRouter.getAvailableProviders();
        var details = providers.stream().collect(toMap(
            p -> p, 
            p -> Map.of(
                "status", circuitBreakers.get(p).getState().name(),
                "latencyMs", healthController.getAvgLatency(p)
            )
        ));
        return Health.up().withDetails(details).build();
    }
}
```
**Endpoint**: `GET /actuator/health` → shows provider status, circuit breaker state, cost tracking

---

## 7. Data Models (Database Schema)

### 7.1 Flyway Migrations
```
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
    severity VARCHAR(16),  -- INFO, LOW, MEDIUM, HIGH, CRITICAL
    file VARCHAR(512),
    line INT,
    message TEXT,
    first_seen_round INT NOT NULL,
    fixed_in_round INT,
    status VARCHAR(16)  -- OPEN, FIXED, REGRESSED, ACCEPTED
);
CREATE INDEX idx_findings_job ON findings(job_id);
CREATE INDEX idx_findings_fingerprint ON findings(fingerprint);

-- rounds
CREATE TABLE rounds (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    round_num INT NOT NULL,
    build_ok BOOLEAN,
    tests_passed INT,
    tests_failed INT,
    coverage DECIMAL(5,2),
    cost_usd DECIMAL(10,6),
    duration_sec INT
);
```

---

## 8. API Contracts (Internal - Engine Interfaces)

### 8.1 ModelRouter
```java
interface ModelRouter {
    Mono<ChatResponse> routeChat(ChatRequest request, ModelProfile profile);
    Mono<ChatResponse> chat(ChatRequest request);  // default profile
    Mono<ChatResponse> chatWithProfile(ChatRequest request, ModelProfile profile);
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
    Tool getTool(String name);
}
```

### 8.3 ToolRegistry (Common Tools)
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
| `dockerBuild` | `{path, tag, dockerfile, buildArgs, timeout}` | `{exitCode, output, success, imageId}` |

---

## 9. Configuration Reference

### 9.1 application.yml (Complete)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:orchestrator
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        model: text-embedding-3-small
        dimensions: 1536
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.2
          max-tokens: 8192

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

orchestrator:
  api-key: ${ORCHESTRATOR_API_KEY:}
  model-router:
    default-provider: nvidia
    timeout-seconds: 60
    max-retries: 3
    retry-delay-ms: 1000
    default-profile: general
    circuit-breaker:
      enabled: true
      failure-threshold: 5
      timeout-seconds: 30
      half-open-requests: 3
    cost-tracking:
      enabled: true
      budget-per-task-usd: 0.50
      budget-per-day-usd: 10.0
      alert-threshold-percent: 80.0
    providers:
      nvidia:
        enabled: true
        base-url: https://integrate.api.nvidia.com/v1
        api-key: ${NVIDIA_API_KEY:}
        models:
          planner: nvidia/nemotron-3-ultra
          coder: nvidia/nemotron-3-ultra
          reviewer: nvidia/nemotron-3-ultra
          security: nvidia/nemotron-3-ultra
          docs: nvidia/nemotron-3-ultra
          general: nvidia/nemotron-3-ultra
        rate-limit-rpm: 60
        timeout-seconds: 60
        supports-streaming: true
      openrouter:
        enabled: false
        base-url: https://openrouter.ai/api/v1
        api-key: ${OPENROUTER_API_KEY:}
      ollama:
        enabled: false
        base-url: http://localhost:11434/v1
      kaggle:
        enabled: false
        base-url: https://www.kaggle.com/api/v1
        api-key: ${KAGGLE_API_KEY:}
    profiles:
      planner:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.1
        max-tokens: 4096
        top-p: 0.9
        system-prompt: "You are an expert software architect..."
        enabled: true
        fallback-profiles: [coder, general]
      coder:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.2
        max-tokens: 8192
        top-p: 0.95
        system-prompt: "You are an expert software engineer..."
        enabled: true
        fallback-profiles: [reviewer, general]
      reviewer:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.1
        max-tokens: 4096
        top-p: 0.9
        system-prompt: "You are a senior code reviewer..."
        enabled: true
        fallback-profiles: [security_reasoning, general]
      security_reasoning:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.0
        max-tokens: 4096
        top-p: 1.0
        system-prompt: "You are a security expert..."
        enabled: true
        fallback-profiles: [reviewer, general]
      documentation:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.3
        max-tokens: 4096
        top-p: 0.9
        system-prompt: "You are a technical writer..."
        enabled: true
        fallback-profiles: [general]
      general:
        provider: nvidia
        model: nvidia/nemotron-3-ultra
        temperature: 0.5
        max-tokens: 4096
        top-p: 0.95
        system-prompt: "You are a helpful AI assistant..."
        enabled: true
        fallback-profiles: []

  pipeline:
    target-root: ${PIPELINE_TARGET_ROOT:.}
    source-file: ${PIPELINE_SOURCE_FILE}
    test-file: ${PIPELINE_TEST_FILE}
    semgrep-path: ${SEMGREP_PATH:semgrep}
    max-rounds: 5
    package-name: ${PIPELINE_PACKAGE_NAME}
    class-name: ${PIPELINE_CLASS_NAME}

orchestrator:
  api-key: ${ORCHESTRATOR_API_KEY:}
  security:
    jwt:
      secret: ${JWT_SECRET:}
      expiration-minutes: 60
      refresh-expiration-hours: 168
      issuer: orchestrator
  rate-limit:
    enabled: false
    default:
      requests-per-minute: 60
      requests-per-hour: 1000
```

---

## 10. Testing Architecture

### 10.1 Test Pyramid
```
                    ┌─────────────┐
                    │   E2E (5)   │  ← Golden specs, real NVIDIA API
                    ├─────────────┤
                    │ Integration │  ← DB, sandbox, external tools
                    ├─────────────┤
                    │   Unit      │  ← 70%+ coverage, FakeModel, FakeProvider
                    └─────────────┘
```

### 10.2 Test Organization
```
src/test/java/com/example/orchestrator/
├── context/ContextManagerTest.java
├── integration/
│   ├── ExecutionIntegrationTest.java
│   └── NvidiaE2ETest.java          (Phase 1.12)
├── memory/ProjectMemoryServiceTest.java
├── model/router/ToolCallParserTest.java
├── permission/PermissionManagerTest.java
├── project/ProjectManagerTest.java
├── repair/RepairLoopServiceTest.java
├── tool/impl/
│   ├── DependencyCheckToolTest.java
│   ├── DockerBuildToolTest.java
│   ├── ListDirectoryToolTest.java
│   ├── SecretScanToolTest.java
│   ├── SemgrepScanToolTest.java
│   ├── TrivyScanToolTest.java
│   └── DockerImageScanToolTest.java
├── verification/VerificationControllerTest.java
└── config/TestModelProviderConfig.java
├── config/TestModelRouterConfig.java
└── FreshOrchestratorApplicationTests.java
```

### 10.3 Test Configuration
```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop

orchestrator:
  cli:
    enabled: false
  model-router:
    default-provider: test
    providers:
      test:
        enabled: true
        base-url: https://test.api/v1
        api-key: test-key
      nvidia:
        enabled: false
    profiles:
      planner:
        provider: test
        model: test-model
        # ... all profiles use "test" provider
```

### 10.4 Test Doubles
```java
@TestConfiguration
@Profile("test")
class TestModelProviderConfig {
    @Bean @Primary @ConditionalOnMissingBean
    ModelProvider testModelProvider() {
        return new ModelProvider() {
            public String getProviderName() { return "test"; }
            public boolean isAvailable() { return true; }
            public boolean supportsStreaming() { return false; }
            public Mono<ChatResponse> chat(ChatRequest request) {
                // Return canned response matching request
            }
        };
    }
}
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
COPY --from=maven:3.9 /usr/share/maven /usr/share/maven
ENTRYPOINT ["java", "-jar", "/app/companion.jar"]
```

### 11.3 Production Deployment (v1)
```bash
# Single VPS with Docker Compose
# services: api, worker, postgres, web-ui (static), nginx
# Reverse proxy with auto HTTPS
```

---

## 12. Appendix: Key Interfaces Summary

| Interface | Module | Purpose |
|-----------|--------|---------|
| `ModelProvider` | engine-llm | LLM abstraction |
| `ModelRouter` | engine-llm | Routing, failover, cost, circuit breaker |
| `Tool` | tool | Tool execution interface |
| `ToolRegistry` | tool | Tool discovery + execution |
| `ToolSpec` | tool | JSON schema for tool I/O |
| `ToolResult` | tool | Standardized tool output |
| `VerificationResult` | engine-verify | Build/tests/findings summary |
| `ProviderHealthController` | api | `/api/health/providers` endpoint |
| `CostTracker` | engine-llm | Token counting, budget enforcement |
| `CircuitBreaker` | engine-llm | Failure threshold + cooldown |
| `ArtifactStore` | engine-store | File + DB persistence |
| `SecurityEnforcer` | security | Pre-write validation |

---

*This architecture document is a living document. Update with each architectural decision.*