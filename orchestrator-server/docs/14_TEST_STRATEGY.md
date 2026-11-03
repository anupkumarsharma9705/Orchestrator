# Test Strategy
## Java Companion Orchestrator — v1 CLI MVP

---

## 1. Testing Philosophy

### 1.1 Principles
- **Test Pyramid**: Many fast unit tests → some integration tests → few E2E tests
- **Fast Feedback**: Unit tests < 1s, Integration < 30s, E2E < 5 min
- **Deterministic**: No flaky tests; fixed seeds, no real network in unit tests
- **Fake First**: `FakeModel` + `FakeProvider` for all unit tests (zero cost, zero latency)
- **Real in CI**: Real NVIDIA API + Docker only in CI (scheduled, not every PR)

### 1.2 Test Classification
| Layer | Scope | Tools | Speed | % of Tests |
|-------|-------|-------|-------|------------|
| **Unit** | Single class, mocked deps | JUnit 5, Mockito, FakeModel | < 100ms | 70% |
| **Integration** | Module + real DB/Docker | Testcontainers, H2, Docker | 10-30s | 20% |
| **E2E** | Full pipeline (CLI → verify) | Spring Boot Test, Real NVIDIA | 2-5 min | 10% |

---

## 2. Test Organization

### 2.1 Package Structure
```text
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
└── config/
    ├── TestModelProviderConfig.java
    ├── TestModelRouterConfig.java
    └── BaseIntegrationTest.java
```

### 2.2 Test Doubles
| Double | Purpose | Implementation |
|--------|---------|----------------|
| `FakeModel` | Canned responses for all profiles | `engine-llm` test scope |
| `TestModelProvider` | `ModelProvider` bean for tests | `@TestConfiguration` |
| `TestModelRouterConfig` | Full `ModelRouter` with test provider | `@Profile("test")` |

---

## 3. Unit Testing (70%)

### 3.1 engine-model
| Class | Test Focus | Coverage Target |
|-------|------------|-----------------|
| `Requirement` | Validation, JSON round-trip | 100% |
| `PlanTask` | Dependency validation, topological sort | 100% |
| `Finding` | Fingerprint stability (line changes) | 100% |
| `Fix` | Result enum, file list serialization | 100% |
| `Job`, `Round`, `Fix`, `Approval` | State transitions, JSON | 90% |

### 3.2 engine-llm
| Component | Test Focus | Key Scenarios |
|-----------|------------|---------------|
| `ModelRouter` | Primary → fallback → any available | Primary OK, primary fails → fallback, all fail → error |
| `CircuitBreaker` | 3 failures → OPEN → 60s → HALF_OPEN → CLOSED | Threshold, timeout, half-open success |
| `CostTracker` | Token counting, micro-dollar math, budget enforcement | Normal, budget exceeded, daily reset |
| `NvidiaProvider` | Request mapping, response parsing, null usage | Happy path, null usage, 4xx/5xx errors |

**Key Test: Failover**
```java
@Test
void failoverToFallback() {
    // Primary fails → fallback answers
    when(primary.chat(any())).thenReturn(Mono.error(new RuntimeException("down")));
    when(fallback.chat(any())).thenReturn(Mono.just(cannedResponse()));
    
    var response = router.routeChat(request, PLANNER).block();
    
    assertEquals("fallback", response.provider());
}
```

### 3.3 engine-core
| Component | Test Focus |
|-----------|------------|
| `Clarifier` | ≤8 questions, defaults, follow-up round |
| `Refiner` | All 10 sections present, req IDs referenced |
| `Planner` | No cycles, all req IDs covered, topological order |
| `Coder` | Tool call parsing, staging → promote → compile |
| `Fixer` | Group by file, CRITICAL first, regression guard |

### 3.4 engine-verify
| Parser | Sample Report | Assertions |
|--------|---------------|------------|
| SpotBugs | `spotbugs-report.xml` | Finding count, severity mapping |
| Checkstyle | `checkstyle-report.xml` | Severity mapping |
| Semgrep | `semgrep.json` | Rule ID, file, line, message |
| Trivy | `trivy-report.json` | CVE parsing, severity |
| DependencyCheck | `depcheck-report.json` | CVE + severity |
| SecretScan | `secret-report.json` | Redaction in output |

### 3.4 engine-sandbox
| Scenario | Expected |
|----------|----------|
| Normal compile | Exit 0, stdout captured |
| Compile error | Exit 1, stderr captured |
| Timeout | Container killed, `ToolResult.failure` |
| OOM | Container killed, OOM message |
| Path traversal attempt | `ToolResult.failure` (path check) |
| Symlink escape | `ToolResult.failure` (realpath check) |

### 3.5 engine-store
| Repository | Test Scenarios |
|------------|----------------|
| `JobRepository` | CRUD, find by project/state, latest by project |
| `FindingRepository` | By job, by fingerprint, by severity |
| `CostAggregateRepository` | Singleton upsert, micro-dollar math |

---

## 4. Integration Testing (20%)

### 4.1 Test Infrastructure
```java
// BaseIntegrationTest.java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml", inheritLocations = false)
@DirtiesContext(classMode = AFTER_CLASS)
abstract class BaseIntegrationTest {
    @Autowired protected ModelRouter modelRouter;
    @Autowired protected ToolRegistry toolRegistry;
    @Autowired protected ExecutionEngine executionEngine;
    @Autowired protected ProjectManager projectManager;
    @Autowired protected Verifier verifier;
    @Autowired protected CostTracker costTracker;
}
```

### 4.2 Key Integration Tests

#### ExecutionIntegrationTest
```java
@SpringBootTest @ActiveProfiles("test") @Disabled("Requires OPENROUTER_API_KEY")
class ExecutionIntegrationTest extends BaseIntegrationTest {
    @TempDir Path tempDir;
    
    @Test @Disabled
    void shouldExecuteReadOnlyTaskEndToEnd() throws IOException {
        // 1. Create Spring Boot project with Controller
        // 2. Register project
        // 3. Submit task: "List all Java files"
        // 4. Wait for WAITING_APPROVAL
        // 5. Approve
        // 6. Wait for COMPLETED
        // 7. Assert tool results, files created
    }
}
```

#### VerificationControllerTest
```java
class VerificationControllerTest extends BaseIntegrationTest {
    @Autowired VerificationController controller;
    @Autowired ToolRegistry toolRegistry;
    @TempDir Path tempDir;
    
    @Test void shouldCreateDefaultConfig() { ... }
    @Test void shouldCreateFullConfig() { ... }
    @Test void shouldRunCompileVerification() throws IOException { ... }
}
```

#### Tool Integration Tests
```java
// Each tool: DockerBuild, Semgrep, Trivy, DepCheck, SecretScan
@Test void shouldExecuteSemgrepScan() throws IOException {
    // Write vulnerable Java file
    // Run SemgrepScanTool
    // Assert tool executes (may succeed/fail based on semgrep availability)
    // Assert result structure valid
}
```

### 4.3 Test Containers
```java
@Testcontainers
class PostgresIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

---

## 5. E2E Testing (10%)

### 5.1 Golden Specs (30 Total)
| Category | Count | Examples |
|----------|-------|----------|
| Simple CRUD | 10 | Book, User, Product, Order, Task, ... |
| Auth + Relations | 10 | User+Role, Book+Author, Order+Item, ... |
| Business Rules | 10 | Stock check, booking conflict, state machine |

### 5.2 E2E Test Runner
```java
// eval/EvalRunner.java
@SpringBootTest
class EvalRunner {
    @Test void runGoldenSpecs() {
        List<GoldenSpec> specs = loadSpecs("eval/specs/");
        List<EvalResult> results = specs.parallelStream()
            .map(this::runSpec)
            .toList();
        
        // Metrics table
        printTable(results);
        // Assert Gate 1 targets
        assertCompileSuccessRate(results, 0.90);
        assertTestsPassRate(results, 0.85);
        assertHighCriticalZero(results, 8); // 8/10 specs
    }
}
```

### 5.3 E2E Test Config (`application-e2e.yml`)
```yaml
spring:
  datasource:
    url: jdbc:sqlite:e2e-test.db
  flyway:
    enabled: true

orchestrator:
  model-router:
    default-provider: nvidia
    providers:
      nvidia:
        enabled: true
        api-key: ${NVIDIA_API_KEY}
      test:
        enabled: false
```

---

## 5. Test Data & Fixtures

### 5.1 Golden Specs Format (`eval/specs/`)
```json
{
  "id": "crud-book-01",
  "category": "crud",
  "name": "Book CRUD with Author",
  "request": "REST CRUD for Book entity with author, genre, ISBN, pagination, validation",
  "clarificationAnswers": {
    "database": "postgresql",
    "auth": "jwt",
    "apiStyle": "rest",
    "tests": "junit5-testcontainers",
    "packaging": "jar"
  },
  "expected": {
    "compileSuccess": true,
    "minTestsPassed": 15,
    "maxHighCriticalFindings": 0,
    "maxRegressions": 0,
    "maxRounds": 3,
    "maxCostUsd": 0.50
  }
}
```

### 5.2 Test Projects (`eval/test-projects/`)
```text
eval/test-projects/
├── vulnerable-spring-boot/     # SQLi, XSS, path traversal
├── outdated-dependencies/      # Known CVEs
├── missing-tests/              # Low coverage
├── style-violations/           # Checkstyle violations
└── secret-leaks/               # Hardcoded secrets
```

---

## 6. CI/CD Pipeline

### 6.1 GitHub Actions Workflow
```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - uses: actions/setup-node@v4  # for web-ui later
      - name: Cache Maven
        uses: actions/cache@v4
        with: { path: ~/.m2/repository, key: maven-${{ hashFiles('**/pom.xml') }} }
      - run: ./mvnw verify -DskipE2E
      
  e2e-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    if: github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'
    env:
      NVIDIA_API_KEY: ${{ secrets.NVIDIA_API_KEY }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - name: Start Docker
        run: sudo systemctl start docker
      - run: ./mvnw test -Dtest=NvidiaE2ETest -DfailIfNoTests=false
      
  release:
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/')
    needs: [build-and-test]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: ./mvnw clean package -DskipTests
      - uses: softprops/action-gh-release@v1
        with: { files: target/*.jar, generate_release_notes: true }
```

### 5.2 Scheduled Runs
```yaml
# .github/workflows/scheduled.yml
on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly Sunday 2 AM
  workflow_dispatch:
jobs:
  e2e-full:
    uses: ./.github/workflows/ci.yml
    secrets: inherit
```

---

## 6. Test Execution Commands

```bash
# Unit tests only (fast, no Docker)
./mvnw test -pl engine-model,engine-llm,engine-core,engine-verify,engine-store

# Integration tests (needs Docker)
./mvnw test -pl engine-sandbox,engine-verify -Dtest=*IntegrationTest

# E2E with real NVIDIA API (scheduled, manual trigger)
./mvnw test -Dtest=NvidiaE2ETest -DfailIfNoTests=false -Dnvidia.api.key=$NVIDIA_API_KEY

# Full verify (CI)
./mvnw verify

# Single test class
./mvnw test -Dtest=ModelRouterTest#failoverToFallback

# Coverage report
./mvnw jacoco:report
open target/site/jacoco/index.html
```

---

## 7. Coverage Gates

```xml
<!-- pom.xml - jacoco-maven-plugin -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.70</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.50</minimum>
                    </limit>
                </limits>
            </rule>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
    <executions>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

**Gate 1 Coverage Targets:**
- Engine modules: ≥ 70% line, 50% branch
- Overall: ≥ 70% line
- New code: ≥ 80% line

---

## 6. Test Data Management

### 6.1 Test Fixtures
```text
src/test/resources/
├── spotbugs-report.xml
├── checkstyle-report.xml
├── semgrep-report.json
├── trivy-report.json
├── dependency-check-report.json
├── secret-scan-report.json
└── test-projects/
    ├── simple-crud/
    │   ├── pom.xml
    │   └── src/main/java/.../Book.java
    ├── vulnerable-spring/
    │   └── src/main/java/.../UnsafeController.java
    └── outdated-deps/
        └── pom.xml (with known CVEs)
```

### 6.2 Test Data Builders
```java
@TestDataBuilder
class JobBuilder {
    private Job.JobBuilder builder = Job.builder()
        .id("test-job-" + UUID.randomUUID().toString().substring(0, 8))
        .projectId("test-project")
        .state(JobState.BUILDING)
        .round(1);
    
    public JobBuilder withState(JobState state) { builder.state(state); return this; }
    public JobBuilder withRound(int round) { builder.round(round); return this; }
    public Job build() { return builder.build(); }
}
```

---

## 7. Mutation Testing (Optional, Post-v1)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <configuration>
        <targetClasses>
            <param>com.example.orchestrator.*</param>
        </targetClasses>
        <targetTests>
            <param>com.example.orchestrator.*</param>
        </targetTests>
        <mutators>
            <mutator>CONDITIONALS_BOUNDARY</mutator>
            <mutator>INVERT_NEGS</mutator>
            <mutator>MATH</mutator>
            <mutator>NEGATE_CONDITIONALS</mutator>
        </mutators>
    </configuration>
</plugin>
```
**Run**: `./mvnw org.pitest:pitest-maven:mutationCoverage`
**Target**: ≥ 70% mutation score on engine-core

---

## 7. Contract Testing (Future)

```java
// Spring Cloud Contract for CLI ↔ Engine compatibility
@AutoConfigureStubRunner(ids = "com.example:engine-core:+:stubs:8080")
class CliContractTest {
    @Test void generateCommandContract() { ... }
}
```

---

## 8. Performance Benchmarks

| Operation | Target | Measurement |
|-----------|---------|-------------|
| ModelRouter failover | < 500ms | JMH benchmark |
| Verifier (7 scanners) | < 60s | `mvn verify` on skeleton |
| Sandbox startup | < 5s | JMH benchmark |
| CostTracker.recordUsage | < 1ms | JMH benchmark |
| Fingerprint computation | < 0.1ms | JMH benchmark |

---

## 8. Test Execution Checklist

### Pre-Commit (Local)
- [ ] `./mvnw test -pl engine-model,engine-llm,engine-core,engine-verify,engine-store`
- [ ] `./mvnw checkstyle:check spotbugs:check`

### Pre-Push (CI)
- [ ] `./mvnw verify -DskipE2E`
- [ ] `./mvnw test -pl engine-sandbox,engine-verify -Dtest=*IntegrationTest`

### Nightly/Scheduled (GitHub Actions)
- [ ] Full E2E with real NVIDIA API
- [ ] External tool integration (Docker, Semgrep, Trivy, DepCheck)
- [ ] Load test (50 concurrent jobs)

### Release (Tag)
- [ ] All above + `./mvnw clean verify`
- [ ] Eval runner on 10 specs → metrics table
- [ ] Mutation testing (post-v1)

---

*This testing strategy ensures quality at every layer while maintaining fast feedback loops for a solo developer.*