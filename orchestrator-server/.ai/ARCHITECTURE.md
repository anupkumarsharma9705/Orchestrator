# Architecture

## System Overview

```
User / OpenCode / Aider / CLI
        |
        v
REST API (TaskController)
        |
        v
Orchestration Layer
    - ExecutionEngine (async task orchestration)
    - RepairLoopService (bounded repair attempts)
        |
        v
Core Services
    - ProjectManager (project isolation)
    - ProjectMemoryService (.ai/ memory per project)
    - ContextManager (deterministic file selection)
    - RequirementCompiler (NL -> structured requirements)
    - TaskPlanner (requirements -> ordered task DAG)
    - VerificationController (compile -> test -> security -> docker)
        |
        v
Tool Layer (PermissionManager enforced)
    - ToolRegistry (9 tools registered)
    - SAFE: listDirectory, readFile, searchFiles, gitStatus, gitDiff, runMavenTest
    - CONTROLLED: writeFile, editFile, runMavenBuild, semgrepScan, dependencyCheck, trivyScan, secretScan, dockerBuild, dockerImageScan
    - APPROVAL_REQUIRED: (reserved)
    - HUMAN_ONLY: (reserved)
    - PermissionManager: enforces risk levels via PermissionContext
        |
        v
External Systems
    - Spring AI (OpenRouter/OpenAI-compatible)
    - Semgrep (static analysis)
    - OWASP Dependency-Check (vulnerable deps)
    - Trivy (vulnerability scanner)
    - Docker (build + image scan)
    - Maven (build/test)
    - Git (status/diff)
    - Filesystem (read/write/list/search)
    - PostgreSQL (future: job persistence)
    - Redis (future: caching/queue)
```

## Component Boundaries

### orchestrator (this module)
- REST API endpoints
- Orchestration logic
- Tool registry and execution
- Permission enforcement
- Memory management
- Context selection

### Projects (external)
- Each project has isolated `.ai/` memory
- Projects registered via `ProjectManager`
- Build tools: Maven (default), Gradle (configurable)
- Git repository per project

### AI Providers (pluggable)
- OpenRouter (current)
- Ollama (planned)
- Kaggle (planned)
- Role-based profiles: PLANNER, CODER, REVIEWER, SECURITY_REASONING, DOCUMENTATION

## Data Flow

```
Task Request
    |
    v
ProjectManager.get(projectId)
    |
    v
ProjectMemoryService.loadMemory()
    |
    v
ContextManager.selectContext(request)
    |
    v
RequirementCompiler.compile(context) -> RequirementSpec
    |
    v
TaskPlanner.plan(requirements, context) -> TaskPlan
    |
    v
ApprovalService.requestApproval() -> wait for human
    |
    v
ExecutionEngine.executePlan(TaskPlan)
    |
    v
For each task:
    For each tool in task.tools:
        PermissionManager.checkPermission(riskLevel, context)
        ToolRegistry.execute(tool, input, context)
        AuditService.logToolExecution()
    |
    v
VerificationController.runVerification()
    |
    v
If failed & attempts < max: RepairLoopService.startRepairLoop()
    |
    v
VerificationResult -> Report
```

## Technology Stack
- Java 21
- Spring Boot 4.1.1
- Spring AI 2.0.1 (OpenRouter)
- Maven 3.9+
- Jackson (JSON)
- JUnit 5 (testing)
- PostgreSQL (future)
- Docker (verification)