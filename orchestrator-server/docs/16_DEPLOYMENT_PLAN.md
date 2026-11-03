# Deployment Plan
## Java Companion Orchestrator — v1 CLI MVP

---

## 1. Deployment Overview

### 1.1 Deployment Model
| Aspect | v1 Approach |
|--------|-------------|
| **Distribution** | Executable JAR (`companion.jar`) via GitHub Releases |
| **Installation** | Manual download + `java -jar` (launchers for Windows/Mac/Linux) |
| **Runtime** | User's machine (Java 21 + Docker) |
| **Data** | Local SQLite file (`orchestrator.db`) + `.companion/` folder |
| **Updates** | Manual re-download (auto-update in v1.1+) |

### 1.2 Target Environments
| Environment | Support |
|-------------|---------|
| **Linux** (Ubuntu 22.04+, Fedora 38+, Arch) | ✅ Full |
| **macOS** (Intel + Apple Silicon, 13+) | ✅ Full |
| **Windows** (10/11, WSL2) | ✅ Full |
| **CI/CD** (GitHub Actions, GitLab CI) | ✅ `companion verify` / `fix` |

---

## 2. Build & Release Pipeline

### 2.1 Build Process
```bash
# Local build
./mvnw clean package -DskipTests

# Produces:
# - target/companion-<version>.jar (executable, all deps)
# - target/companion-<version>-sources.jar
# - target/companion-<version>-javadoc.jar
```

### 2.2 Release Workflow (GitHub Actions)
```yaml
# .github/workflows/release.yml
on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: ./mvnw clean verify -DskipE2E
      
  package:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: ./mvnw clean package -DskipTests
      - uses: actions/upload-artifact@v4
        with: 
          name: companion-jar
          path: target/companion-*.jar
          
  release:
    needs: package
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - uses: actions/download-artifact@v4
        with: { name: companion-jar }
      - uses: softprops/action-gh-release@v1
        with:
          files: companion-*.jar
          generate_release_notes: true
          draft: false
          prerelease: false
```

### 2.3 Versioning
```text
MAJOR.MINOR.PATCH
1.0.0  → 1.0.1 (hotfix) → 1.1.0 (feature) → 2.0.0 (breaking)
```

- **Breaking changes**: CLI flag changes, artifact format, config schema
- **Documentation**: `CHANGELOG.md` + GitHub Release notes

---

## 3. Artifacts & Distribution

### 3.1 Release Artifacts
| Artifact | Purpose |
|----------|---------|
| `companion-<version>.jar` | Main executable (Linux/macOS/Windows) |
| `companion-<version>-windows.exe` | Windows native launcher (JReleaser) |
| `companion-<version>-macos.dmg` | macOS installer (JReleaser) |
| `companion-<version>-linux.tar.gz` | Linux tarball with launcher script |
| `companion-<version>-sha256sum.txt` | Checksums for verification |

### 3.2 JReleaser Configuration
```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jreleaser</groupId>
    <artifactId>jreleaser-maven-plugin</artifactId>
    <configuration>
        <project>
            <name>companion</name>
            <description>Java Companion Orchestrator CLI</description>
            <license>Apache-2.0</license>
            <authors>
                <author>Your Name</author>
            </authors>
            <links>
                <website>https://github.com/yourusername/java-companion</website>
                <repository>https://github.com/yourusername/java-companion</repository>
            </links>
        </project>
        <assemble>
            <jlink>
                <imageName>companion</imageName>
                <jdk>jdk-21</jdk>
            </jlink>
        </assemble>
        <distributions>
            <distribution>
                <name>companion</name>
                <artifact>
                    <pattern>target/companion-${project.version}.jar</pattern>
                </artifact>
            </distribution>
        </distributions>
    </configuration>
</plugin>
```

### 3.3 Platform Launchers
| Platform | Artifact | Install Method |
|----------|----------|----------------|
| **Linux** | `companion` (shell script + JAR) | `curl -fsSL ... \| sh` |
| **macOS** | `companion.dmg` / `companion.pkg` | Drag to Applications / `brew install` |
| **Windows** | `companion.exe` (Launch4j/JReleaser) | `scoop install companion` / Download .exe |
| **Universal** | `companion.jar` | `java -jar companion.jar` |

---

## 4. Installation & Prerequisites

### 4.1 System Requirements
| Component | Minimum Version | Check Command |
|-----------|-----------------|---------------|
| **Java** | 21 (LTS) | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Docker** | 24+ (Desktop/Engine) | `docker --version` |
| **Git** | 2.40+ | `git --version` |
| **SQLite** | 3.40+ | `sqlite3 --version` |

### 4.2 Installation Methods

#### Option A: Package Managers (Post-launch)
```bash
# macOS (Homebrew)
brew install java-companion

# Windows (Scoop)
scoop install companion

# Linux (Arch/AUR)
yay -S java-companion
```

#### Option B: Manual Download (v1)
```bash
# 1. Download from GitHub Releases
# 2. Make executable (Linux/macOS)
chmod +x companion.jar

# 3. Run
java -jar companion.jar --help
```

#### Option C: Windows Launcher
```powershell
# Download companion.exe from Releases
# Run directly
.\companion.exe --help
```

### 4.3 First-Run Setup
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

💡 Run `companion config set nvidia.api-key "nvapi-xxx"` to configure
```

---

## 5. Configuration

### 5.1 Config Files
| File | Location | Purpose |
|------|----------|---------|
| User config | `~/.companion/config.yml` | API keys, budgets, defaults |
| Project config | `<project>/.companion/config.yml` | Project-specific settings |
| License key (v1.1+) | `~/.companion/license.key` | Offline validation |

### 5.2 Config Schema
```yaml
# ~/.companion/config.yml
api-key: ""                          # Future web API
model-router:
  default-provider: nvidia
  providers:
    nvidia:
      api-key: ""                    # Set via CLI
    openrouter:
      api-key: ""
    ollama:
      enabled: false
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

### 5.3 Environment Variables (Override Config)
```bash
export NVIDIA_API_KEY="nvapi-xxx"
export OPENROUTER_API_KEY="sk-or-xxx"
export OLLAMA_HOST="http://localhost:11434"
export ORCHESTRATOR_API_KEY="orch_sk_xxx"
export JWT_SECRET="..."              # v1.1+
export GITHUB_TOKEN="ghp_xxx"        # For PR creation
export NVD_API_KEY="nvd-xxx"         # For DepCheck
```

---

## 6. Runtime Operations

### 6.1 Typical Workflow
```bash
# 1. Generate new project
companion generate "REST CRUD for Book with author, genre, ISBN" --name library

# 2. Verify existing project
companion verify ./my-spring-project --scanners semgrep,spotbugs,depcheck

# 3. Fix existing project
companion fix ./my-project --max-rounds 3 --only security

# 4. Check job status
companion status job-abc123

# 5. Approve/reject plan
companion approve job-abc123 --note "Looks good"

# 6. View config
companion config list
companion config get nvidia.api-key
```

### 6.2 Offline Mode
```bash
# Use local Ollama model (no API key needed)
companion generate "REST CRUD for Book" --name library --offline

# Requires: ollama serve running locally with codellama:13b
```

### 6.3 CI/CD Integration
```yaml
# .github/workflows/verify.yml
- name: Verify Spring Boot Project
  run: |
    java -jar companion.jar verify ./project \
      --scanners semgrep,spotbugs,depcheck \
      --fail-on HIGH \
      --format sarif \
      --output verification.sarif
```

---

## 7. Monitoring & Observability

### 7.1 Health Checks
```bash
# Built-in health check (via Actuator)
java -jar companion.jar --health
# or
curl http://localhost:8080/actuator/health  # if web server enabled in v1.1+
```

### 7.2 Logging
```bash
# Structured JSON logs (production)
java -jar companion.jar generate "..." --json --verbose

# Log levels
logging:
  level:
    com.example.orchestrator: DEBUG
    org.springframework: WARN
```

### 7.3 Metrics (Micrometer + Prometheus)
```text
# Key metrics exposed at /actuator/prometheus
orchestrator_jobs_started_total
orchestrator_jobs_completed_total
orchestrator_jobs_failed_total
orchestrator_llm_tokens_total{prompt|completion|total}
orchestrator_llm_cost_usd_total
orchestrator_verifier_findings_total{severity}
orchestrator_sandbox_duration_seconds
```

---

## 8. Backup & Recovery

### 8.1 Data Backup
| Data | Location | Backup Method |
|------|----------|---------------|
| **SQLite DB** | `orchestrator.db` / `~/.companion/orchestrator.db` | Copy file |
| **Artifacts** | `<project>/.companion/` | Git commit / tar.gz |
| **Config** | `~/.companion/config.yml` | Git / manual copy |

### 8.2 Recovery Procedures
```bash
# Restore from backup
cp backup/orchestrator.db ~/.companion/orchestrator.db
cp -r backup/.companion/project/ ./my-project/.companion/

# Re-register project
companion project register ./my-project
```

---

## 9. Rollback Procedures

### 9.1 CLI Rollback
```bash
# Download previous version
wget https://github.com/.../releases/download/v1.0.0/companion-1.0.0.jar

# Or use package manager
brew switch java-companion 1.0.0
scoop reset companion 1.0.0
```

### 9.2 Database Rollback
```bash
# SQLite: restore from backup
cp backup/orchestrator.db.current orchestrator.db

# Flyway: repair if migration failed
./mvnw flyway:repair
./mvnw flyway:migrate
```

---

## 10. Cost Control

### 10.1 Operational Costs (v1 Launch)
| Item | Cost | Notes |
|------|------|-------|
| GitHub Pro | $0 | Free for public repos |
| Docker Hub | $0 | Public images free |
| NVIDIA API | $0-50/mo | Free tier generous |
| Domain | $12/yr | Namecheap |
| SSL Cert | $0 | Let's Encrypt |
| Monitoring | $0 | UptimeRobot free tier |
| **Total** | **~$12/yr** | Extremely lean |

### 10.2 Per-Project Costs (User Side)
| Resource | Cost |
|----------|------|
| NVIDIA API (free tier) | $0 |
| Ollama (local) | $0 |
| Docker Desktop | Free (personal) / $5-21/user/mo (team) |
| Maven Central | Free |

---

## 11. Security Hardening (Production)

### 11.1 JAR Signing
```bash
# Sign JAR for integrity verification
jarsigner -keystore keystore.jks -storepass $STOREPASS \
  target/companion-1.0.0.jar alias

# Verify
jarsigner -verify -verbose target/companion-1.0.0.jar
```

### 11.2 Checksums
```bash
# Generate SHA256
sha256sum target/companion-1.0.0.jar > target/companion-1.0.0-sha256sum.txt

# Verify
sha256sum -c companion-1.0.0-sha256sum.txt
```

### 11.3 License Enforcement (v1.1+)
```java
// Offline JWT validation (no license server)
@Component
class LicenseValidator {
    boolean validate(String licenseKey) {
        // 1. Parse JWT (signed with RS256)
        // 2. Check expiration
        // 3. Verify signature with embedded public key
        // 4. Check features (offline mode, team size, etc.)
    }
}
```

---

## 12. Rollout Checklist

### Pre-Release
- [ ] All Gate 2 criteria met
- [ ] `mvn verify` passes on main branch
- [ ] Release notes written (`CHANGELOG.md`)
- [ ] Version bumped in `pom.xml` + `VERSIONS.md`
- [ ] Git tag created: `git tag -a v1.0.0 -m "v1.0.0"`
- [ ] GitHub Release drafted with artifacts
- [ ] Checksums generated and uploaded

### Launch Day
- [ ] Publish GitHub Release
- [ ] Announce on: Reddit (r/java, r/SpringBoot), Twitter/X, LinkedIn, Discord
- [ ] Monitor: GitHub Issues, Discussions, download count
- [ ] Respond to first issues within 4 hours (P0/P1)

### Post-Launch (Week 1)
- [ ] Triage issues: P0 (4h), P1 (24h), P2 (72h), P3 (next release)
- [ ] Collect feedback: usability, bugs, feature requests
- [ ] Update docs based on common questions
- [ ] Plan v1.0.1 hotfix if needed

---

## 13. Post-Launch Operations

### 13.1 Release Cadence
| Channel | Cadence | Process |
|---------|---------|---------|
| **Patch** (1.0.x) | As needed | Hotfix → tag → GitHub Release |
| **Minor** (1.x.0) | Monthly | Feature branch → PR → merge → tag |
| **Major** (x.0.0) | Quarterly | RFC → design doc → implementation |

### 13.2 Support Workflow
```text
User Issue → GitHub Issue (auto-labeled) → Triage (P0-P3) → Fix → Release Patch
```

| Priority | SLA | Examples |
|----------|-----|----------|
| **P0 - Critical** | 4 hours | Data loss, security vuln, CLI crashes on startup |
| **P1 - High** | 24 hours | Build fails, verification false positives, config loss |
| **P2 - Medium** | 72 hours | Feature requests, minor bugs, doc typos |
| **P3 - Low** | Next release | Nice-to-haves, docs improvements |

### 13.3 Versioning
```text
MAJOR.MINOR.PATCH
1.0.0  → 1.0.1 (hotfix) → 1.1.0 (feature) → 2.0.0 (breaking)
```

---

## 14. Future Considerations (v1.1+)

| Feature | Deployment Impact |
|---------|-------------------|
| **Auto-update** | Background check on startup; prompt to download new JAR |
| **MCP Server** | Separate process; stdio/HTTP transport; same JAR different entry point |
| **Web Dashboard** | Separate Spring Boot web app; shared engine JAR |
| **IntelliJ Plugin** | Separate plugin project; bundles engine JAR |
| **Team Workspaces** | Multi-user DB; RBAC; shared config; requires web app |

---

*This deployment plan covers v1 MVP. Update with each release and operational learning.*