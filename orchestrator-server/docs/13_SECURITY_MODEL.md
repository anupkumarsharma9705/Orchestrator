# Security Model
## Java Companion Orchestrator — v1 CLI MVP

---

## 1. Security Overview

### 1.1 Security Principles
- **Defense in Depth**: Multiple layers (sandbox, prompt safety, verification, audit)
- **Zero Trust**: Generated code is untrusted until verified
- **Local-First**: Code never leaves user's machine; no cloud dependency for core flow
- **Auditability**: Every finding, fix, and decision tracked with fingerprints
- **Fail Secure**: Budget exceeded → hard stop; sandbox escape → container killed

### 1.2 Threat Model

| Threat | Likelihood | Impact | Mitigation |
|--------|------------|--------|------------|
| **Prompt Injection** | High | High | Strip instruction-like text from project files before LLM |
| **Secret Leakage** | Medium | Critical | Scan prompts for patterns; never log secrets; env vars only |
| **Sandbox Escape** | Low | Critical | Docker: non-root, read-only rootfs, no host mounts, resource caps |
| **Path Traversal** | Medium | High | Path resolution: `basePath.resolve(path).normalize()` + `startsWith(basePath)` + `toRealPath()` |
| **Command Injection** | Low | Critical | No shell execution; `ProcessBuilder` with explicit args |
| **Secret Storage** | Medium | High | Env vars + `~/.companion/keys/` (chmod 600); never in code/logs |
| **Supply Chain** | Low | High | Pinned versions; dependency scanning (DepCheck + Trivy) |
| **Model Poisoning** | Low | High | Single provider (NVIDIA) with pinned model; no user-trained models |

---

## 2. Sandbox Security

### 2.1 Docker Hardening
```dockerfile
# Sandbox container runs with:
docker run \
  --memory=2g \
  --cpus=2 \
  --pids-limit=100 \
  --user=1000:1000 \
  --read-only \
  --tmpfs /tmp \
  --network=none \
  -v workspace:/workspace:rw \
  -v maven-cache:/root/.m2:rw \
  companion-sandbox:latest
```

**Security Properties:**
- **Non-root user**: UID 1000, GID 1000 (no root privileges)
- **Read-only rootfs**: Cannot modify container filesystem
- **No host mounts**: Only workspace and Maven cache mounted
- **No network**: `--network=none` prevents exfiltration/C2
- **Resource caps**: Memory 2GB, CPU 2 cores, 100 processes max
- **Ephemeral**: Container destroyed after each run

### 2.2 Sandbox API
```java
interface SandboxRunner {
    /**
     * Runs command in sandboxed Docker container
     * @param command Command to execute (e.g., "mvn verify")
     * @param workspace Project directory (mounted as /workspace)
     * @param timeout Maximum execution time
     * @return SandboxResult with exitCode, stdout, stderr, durationMs
     */
    SandboxResult run(String command, Path workspace, Duration timeout);
}
```

### 2.3 Sandbox Escape Prevention
| Vector | Mitigation |
|--------|-------------|
| Host filesystem access | Only workspace + Maven cache mounted; read-only rootfs |
| Network exfiltration | `--network=none` |
| Privilege escalation | Non-root user (UID 1000); no `CAP_SYS_ADMIN` |
| Resource exhaustion | `--memory=2g --cpus=2 --pids-limit=100` |
| Container breakout | No `CAP_SYS_ADMIN`; no `/proc`/`/sys` access; read-only rootfs |
| Persistence | Container destroyed after each run (`--rm`) |

---

## 3. Prompt Safety

### 3.1 Prompt Injection Defense
```java
@Component
class PromptSanitizer {
    /**
     * Strips instruction-like text from project files before sending to LLM
     * Removes: "ignore previous instructions", "system:", "assistant:", etc.
     */
    String sanitize(String projectFileContent) {
        // 1. Remove lines matching instruction patterns
        // 2. Truncate to max token limit
        // 3. Redact potential secrets
        return sanitized;
    }
}
```

**Patterns Stripped:**
- `ignore (previous|all) instructions?`
- `system:` / `assistant:` / `user:` prefixes
- `you are now` / `act as` / `pretend to be`
- `disregard` / `forget` / `override`
- Base64/encoded payloads

### 3.2 Secret Detection in Prompts
```java
@Component
class SecretDetector {
    private static final Pattern[] SECRET_PATTERNS = {
        Pattern.compile("(?i)(api[_-]?key|secret|password|token)['\"]?\\s*[:=]\\s*['\"][^'\"]+['\"]"),
        Pattern.compile("(?i)(aws|azure|gcp)[_-]?(access[_-]?key|secret)[_-]?key"),
        Pattern.compile("(?i)jdbc:.*password=[^&\\s]+"),
        Pattern.compile("(?i)private[_-]?key['\"]?\\s*[:=]\\s*['\"][^'\"]+['\"]")
    };
    
    /**
     * Scans prompt for secrets before sending to LLM
     * Returns sanitized prompt + list of redacted items (for logging)
     */
    SanitizedPrompt sanitize(String prompt) { ... }
}
```

**Detected Secret Types:**
- API keys (NVIDIA, OpenAI, AWS, Azure, GCP)
- Database passwords (JDBC URLs)
- Private keys (SSH, TLS, JWT)
- Generic tokens/passwords

---

## 4. Security Rules (Enforced by Verifier)

### 4.1 MUST Rules (Violation = Finding, Tool=rulecheck)

| ID | Rule | Enforcement Tool | Severity |
|----|------|------------------|----------|
| **SEC-1** | Constructor injection only | ArchUnit | HIGH |
| **SEC-2** | No SQL concatenation (parameterized only) | Semgrep | HIGH |
| **SEC-3** | BCrypt/Argon2 for passwords | SpotBugs (FindSecBugs) | HIGH |
| **SEC-4** | SecureRandom for tokens | Semgrep | HIGH |
| **SEC-5** | No MD5/SHA1/DES/RC4/ECB | SpotBugs + Semgrep | HIGH |
| **SEC-6** | @Valid on @RequestBody | ArchUnit | HIGH |
| **SEC-7** | No secrets in code/logs | SecretScan (gitleaks + Semgrep) | HIGH |
| **SEC-8** | CORS allow-list only | Semgrep | HIGH |
| **SEC-9** | Rate limiting on auth endpoints | Semgrep | HIGH |
| **SEC-10** | Actuator health only public | ArchUnit | HIGH |

### 4.2 Rule Implementation Details

#### SEC-1: Constructor Injection Only
```java
// ArchUnit test
@Test
void constructorsOnly() {
    classes()
        .that().areAnnotatedWith(Controller.class)
        .or().areAnnotatedWith(Service.class)
        .or().areAnnotatedWith(Repository.class)
        .should().haveOnlyFinalFields()
        .andShould().haveOnlyConstructors()
        .check(importedClasses);
}
```

#### SEC-2: No SQL Concatenation
```yaml
# Semgrep rule
rules:
  - id: java.sql-injection
    pattern-either:
      - pattern: "$X.createQuery($Y + ...)"
      - pattern: "$X.createNativeQuery($Y + ...)"
    message: "SQL injection risk: string concatenation in query"
    severity: HIGH
```

#### SEC-3: BCrypt/Argon2 for Passwords
```java
// SpotBugs FindSecBugs rule: SEC-3
// Detects: MD5, SHA-1, SHA-256 without salt, plain text passwords
```

#### SEC-6: @Valid on @RequestBody
```java
// ArchUnit test
@Test
void requestBodyValidated() {
    methods()
        .that().areAnnotatedWith(RequestBody.class)
        .should().beAnnotatedWith(Valid.class)
        .check(importedClasses);
}
```

---

## 5. Verification Pipeline Security

### 5.1 7-Scanner Coverage

| Scanner | What It Catches | Tool |
|---------|-----------------|------|
| **Compile** | Syntax errors, type mismatches | `mvn compile` |
| **Tests** | Logic errors, regressions | `mvn verify` (Surefire) |
| **Coverage** | Untested code paths | JaCoCo |
| **Static/Security** | Bugs, vulnerabilities | SpotBugs + FindSecBugs |
| **Code Style** | Consistency, maintainability | Checkstyle |
| **Patterns** | Security anti-patterns | Semgrep (Java + Spring) |
| **Vuln Deps** | Known CVEs in dependencies | OWASP DepCheck + Trivy |
| **Secrets** | Hardcoded secrets | Semgrep secrets / gitleaks |

### 5.2 Finding Model
```java
record Finding(
    String fingerprint,      // SHA256(tool|ruleId|filePath|codeLine)
    String tool,             // semgrep, spotbugs, checkstyle, etc.
    String ruleId,           // e.g., "java.sql-injection"
    Severity severity,       // INFO, LOW, MEDIUM, HIGH, CRITICAL
    String file,             // Relative path
    int line,                // Line number (0 if file-level)
    String message,          // Human-readable description
    int firstSeenRound,      // Round where first detected
    Integer fixedInRound,    // Round where fixed (null if open)
    Status status            // OPEN, FIXED, REGRESSED, ACCEPTED
)
```

**Fingerprint Formula:** `SHA256(tool + "|" + ruleId + "|" + filePath + "|" + trimmedCodeLine)`
- Survives line number changes
- Same finding across rounds = same fingerprint
- Enables regression detection

---

## 6. Fix Loop Security

### 6.1 Regression Guard
```java
class RegressionGuard {
    boolean shouldRevert(List<Finding> previousFindings, List<Finding> currentFindings) {
        int newHighCritical = count(currentFindings, HIGH, CRITICAL) - count(previousFindings, HIGH, CRITICAL);
        int fixedHighCritical = count(previousFindings, HIGH, CRITICAL) - count(currentFindings, HIGH, CRITICAL);
        
        // Revert if regressions exceed fixes
        if (newHighCritical > fixedHighCritical) return true;
        
        // Revert if previously passing test now fails
        if (passingTestNowFails()) return true;
        
        return false;
    }
}
```

**Guard Triggers:**
- New HIGH/CRITICAL > Fixed HIGH/CRITICAL
- Previously passing test now fails
- Fix loop makes no progress (0 fixes in round)

**On Trigger:** Revert last round's commits → retry once with failure context

---

## 7. Cost Tracking & Budget Enforcement

### 7.1 Token Counting
```java
@Component
class CostTracker {
    private final Map<String, ModelPricing> pricingTable;  // provider/model → $/1K tokens
    
    void recordUsage(String taskId, String model, String provider, 
                     int promptTokens, int completionTokens) {
        // 1. Calculate cost: (prompt + completion) * pricePer1K / 1000
        // 2. Persist to SQLite (task_costs, daily_costs, cost_aggregates)
        // 3. Check budgets: per-job + daily
        // 4. Alert at 80% daily budget
        // 5. Hard stop if exceeded → job FAILED (BUDGET_EXCEEDED)
    }
}
```

### 7.2 Budget Enforcement
| Budget Type | Default | Enforcement |
|-------------|---------|-------------|
| Per-job | $0.50 | Hard stop → job FAILED |
| Daily | $10.00 | Hard stop → all jobs paused |
| Alert threshold | 80% | Warning logged + notification |

**Price Table (NVIDIA Nemotron-3-Ultra):**
| Model | Prompt $/1K | Completion $/1K |
|-------|-------------|-----------------|
| nvidia/nemotron-3-ultra | $0.00 (free tier) | $0.00 (free tier) |

---

## 8. Secrets Management

### 8.1 Storage
| Secret | Location | Permissions |
|--------|----------|-------------|
| NVIDIA_API_KEY | `~/.companion/config.yml` or `NVIDIA_API_KEY` env var | User-readable only |
| OPENROUTER_API_KEY | `~/.companion/config.yml` or env var | User-readable only |
| JWT_SECRET (v1.1+) | `~/.companion/keys/jwt.secret` | `chmod 600` |
| License key (v1.1+) | `~/.companion/license.key` | User-readable only |

### 8.2 Key Handling Rules
- **Never** log secrets (redacted in all logs)
- **Never** include in prompts to LLM (stripped by SecretDetector)
- **Never** pass to sandbox (no secret env vars in container)
- **Never** commit to git (`.gitignore` includes `~/.companion/keys/`)
- **Rotate** via `companion config set nvidia.api-key <new-key>`

---

## 9. Logging Safety

### 9.1 Structured Logging
```yaml
logging:
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
  level:
    com.example.orchestrator: DEBUG
    org.springframework: WARN
```

### 9.2 Redaction Rules
```java
@Component
class LogRedactor {
    // Applied to all log output
    String redact(String message) {
        return message
            .replaceAll("(?i)(api[_-]?key|secret|password|token)['\"]?\\s*[:=]\\s*['\"][^'\"]+['\"]", "$1=***REDACTED***")
            .replaceAll("(?i)bearer\\s+[a-zA-Z0-9_-]+", "bearer ***REDACTED***");
    }
}
```

### 9.3 Correlation IDs
- Every log line includes `jobId` (MDC)
- Enables tracing across rounds, tools, threads
- Structured JSON output in production (`--json` flag)

---

## 10. Compliance & Standards

### 10.1 Standards Alignment
| Standard | Coverage |
|----------|----------|
| **OWASP Top 10** | SEC-1 to SEC-10 cover A01-A10 |
| **CWE Top 25** | SEC-1 to SEC-10 cover top CWEs |
| **PCI-DSS** | Audit trail + SEC-7 (secrets) + SEC-3 (passwords) |
| **SOC 2** | Audit trail + access control + monitoring |
| **HIPAA** | Audit trail + encryption (TLS) + access control |

### 10.2 Audit Trail
```markdown
# fixes.md (per round)
## Round 2
- FIXED: sha256:abc123 (semgrep:java.sql-injection) - Fixed SQL injection in BookRepository.java:42
  - Changed: String concatenation → parameterized query
  - Verified: Round 3 re-verify passed

# vulnerabilities.md (per round)
## Round 1
- HIGH: sha256:abc123 (semgrep:java.sql-injection) - SQL injection in BookRepository.java:42
```

---

## 11. Incident Response

### 11.1 Security Incident Classification
| Severity | Response Time | Escalation |
|----------|---------------|------------|
| **Critical** (sandbox escape, secret leak) | 1 hour | Immediate patch + user notification |
| **High** (verification bypass) | 4 hours | Patch in next release |
| **Medium** (false positive/negative) | 24 hours | Rule tuning |
| **Low** (style, cosmetic) | Next release | Backlog |

### 11.2 User Notification
- Critical/High: Email + GitHub Security Advisory + release notes
- Medium/Low: Release notes only

---

## 12. Security Testing

### 12.1 Security Test Suite
```java
@Test
void testSqlInjectionDetected() {
    // Plant SQL injection in generated code
    // Run verifier
    // Assert: HIGH finding with ruleId=java.sql-injection
}

@Test
void testSecretLeakageDetected() {
    // Plant API key in generated code
    // Run verifier
    // Assert: HIGH finding with tool=secretScan
}

@Test
void testSandboxEscapeBlocked() {
    // Attempt to read /etc/passwd from sandbox
    // Assert: SandboxResult.failure with SANDBOX_ESCAPE
}

@Test
void testPromptInjectionBlocked() {
    // Inject "ignore previous instructions" in project file
    // Assert: PromptSanitizer strips it before LLM call
}

@Test
void testBudgetEnforcement() {
    // Configure budget $0.01
    // Run generation exceeding budget
    // Assert: Job FAILED with BUDGET_EXCEEDED
}
```

### 12.2 Penetration Testing (Post-v1)
- Annual third-party pentest
- Bug bounty program (HackerOne)
- Automated dependency scanning (Dependabot + Trivy)

---

## 13. Compliance Evidence

### 13.1 Artifacts for Auditors
| Artifact | Purpose | Retention |
|----------|---------|-----------|
| `requirements.md` | What was requested | Project lifetime |
| `plan.md` | What was planned | Project lifetime |
| `fixes.md` | What was fixed + when | Project lifetime |
| `vulnerabilities.md` | What was found + when | Project lifetime |
| `report.md` | Summary + metrics | Project lifetime |
| SQLite DB | Full audit trail | Project lifetime |

### 13.2 Compliance Checklist
- [ ] All HIGH/CRITICAL findings resolved or ACCEPTED with justification
- [ ] Audit trail complete (no gaps in round history)
- [ ] Fingerprint chain unbroken (no missing rounds)
- [ ] Cost tracking within budget
- [ ] No secrets in artifacts or logs
- [ ] Sandbox isolation verified

---

*This security model is a living document. Update with each security decision and incident.*