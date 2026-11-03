# Security

## Phase 3 Security Controls Implemented

### Security Scanning Tools (4)

1. **SemgrepScanTool** (`semgrepScan`)
   - Configurable rulesets (default: `auto`)
   - JSON output with structured findings (ruleId, severity, message, path, line)
   - Returns success=true even when findings found (exit code 1 = findings, not tool failure)
   - Timeout: 120s default

2. **DependencyCheckTool** (`dependencyCheck`)
   - OWASP Dependency-Check integration
   - Scans for vulnerable dependencies
   - Generates JSON report parsed for vulnerabilities (CVE, severity, description, file)
   - Timeout: 300s default

3. **TrivyScanTool** (`trivyScan`)
   - Supports filesystem (`fs`) and container image scanning
   - JSON output with vulnerabilities (vulnId, severity, title, packageName, installedVersion, fixedVersion)
   - Exit code 0 or 1 = success (vulns found), other = tool failure
   - Timeout: 180s default

4. **SecretScanTool** (`secretScan`)
   - 11 built-in secret patterns:
     - AWS Access Key (AKIA...)
     - AWS Secret Key
     - GitHub Token (gh[pousr]_...)
     - GitLab Token (glpat-...)
     - Slack Token (xox[baprs]-...)
     - Generic API Key
     - Private Keys (RSA/DSA/EC/OPENSSH)
     - JWT Tokens
     - Database URLs with passwords
     - Docker Config Auth
     - Generic Secret/Password patterns
   - Scans files < 1MB, excludes build artifacts, .git, node_modules, etc.
   - Masks actual secrets in output (***REDACTED***)
   - Timeout: 60s default

### Verification Pipeline Security Steps
- semgrep → dependency-check → trivy → secret-scan (all advisory, don't fail fast)
- Docker image scan after build (advisory)
- All security tools are CONTROLLED risk level (require authenticated user)

### Permission Enforcement
- `PermissionManager` enforces 4 risk levels:
  - **SAFE**: Always allowed (listDirectory, readFile, searchFiles, gitStatus, gitDiff, runMavenTest)
  - **CONTROLLED**: Requires authenticated user (writeFile, editFile, runMavenBuild, semgrepScan, dependencyCheck, trivyScan, secretScan, dockerBuild, dockerImageScan)
  - **APPROVAL_REQUIRED**: Requires explicit approval (future: gitPush, productionDeploy)
  - **HUMAN_ONLY**: Requires human actor (future: productionDeploy, destructiveProductionOperation)
- Enforced in `ToolRegistry.execute()` - every tool call validated
- `PermissionContext` tracks: userId, taskId, hasApproval, actorType (ai/human)

### Security Boundaries
- Path containment: All file operations validated against project root
- No arbitrary shell execution - all commands use ProcessBuilder with argument lists
- Output size limits (100KB file reads, 100MB process output)
- Timeouts on all external process execution
- Secrets masked in logs/artifacts
- No git push, production deploy, or destructive operations exposed to AI