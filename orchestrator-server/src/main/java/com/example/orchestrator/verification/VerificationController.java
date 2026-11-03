package com.example.orchestrator.verification;

import com.example.orchestrator.project.Project;
import com.example.orchestrator.tool.ToolRegistry;
import com.example.orchestrator.tool.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class VerificationController {

    private final ToolRegistry toolRegistry;

    public VerificationController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public VerificationResult runVerification(Project project, VerificationConfig config) {
        VerificationResult.Builder resultBuilder = VerificationResult.builder();
        
        // Step 1: Compile
        if (config.isCompileEnabled()) {
            ToolResult compileResult = toolRegistry.execute(
                project, "runMavenBuild", 
                "{\"goals\": [\"compile\"], \"skipTests\": true}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("compile", compileResult);
            if (!compileResult.success()) {
                return resultBuilder.build();
            }
        }
        
        // Step 2: Unit Tests
        if (config.isUnitTestEnabled()) {
            ToolResult testResult = toolRegistry.execute(
                project, "runMavenTest",
                "{}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("unit-test", testResult);
            if (!testResult.success()) {
                return resultBuilder.build();
            }
        }
        
        // Step 3: Semgrep Security Scan
        if (config.isSemgrepEnabled()) {
            ToolResult semgrepResult = toolRegistry.execute(
                project, "semgrepScan",
                "{\"config\": \"auto\", \"timeoutSeconds\": 120}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            // Semgrep returns success=true even when findings found (exit code 1 = findings found)
            // Tool failure is when semgrep fails to run (timeout, not found, etc.)
            resultBuilder.addStep("semgrep", semgrepResult);
            // Don't fail fast on semgrep - it's advisory
        }
        
        // Step 4: OWASP Dependency-Check
        if (config.isDependencyCheckEnabled()) {
            ToolResult depCheckResult = toolRegistry.execute(
                project, "dependencyCheck",
                "{\"timeoutSeconds\": 300}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("dependency-check", depCheckResult);
            // Don't fail fast - advisory
        }
        
        // Step 5: Trivy Scan
        if (config.isTrivyEnabled()) {
            ToolResult trivyResult = toolRegistry.execute(
                project, "trivyScan",
                "{\"target\": \"fs\", \"timeoutSeconds\": 180}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("trivy", trivyResult);
            // Don't fail fast - advisory
        }
        
        // Step 6: Secret Scan
        if (config.isSecretScanEnabled()) {
            ToolResult secretResult = toolRegistry.execute(
                project, "secretScan",
                "{\"timeoutSeconds\": 60}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("secret-scan", secretResult);
            // Don't fail fast - advisory
        }
        
        // Step 7: Docker Build
        if (config.isDockerBuildEnabled()) {
            ToolResult dockerBuildResult = toolRegistry.execute(
                project, "dockerBuild",
                "{\"tag\": \"verification-build:latest\", \"timeoutSeconds\": 300}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("docker-build", dockerBuildResult);
            if (!dockerBuildResult.success()) {
                return resultBuilder.build();
            }
        }
        
        // Step 8: Docker Image Scan
        if (config.isDockerImageScanEnabled()) {
            ToolResult dockerImageScanResult = toolRegistry.execute(
                project, "dockerImageScan",
                "{\"image\": \"verification-build:latest\", \"timeoutSeconds\": 180}",
                com.example.orchestrator.permission.PermissionManager.PermissionContext.builder()
                    .taskId("verification")
                    .userId("system")
                    .actorType("system")
                    .hasApproval(true)
                    .build()
            );
            resultBuilder.addStep("docker-image-scan", dockerImageScanResult);
            // Don't fail fast - advisory
        }
        
        return resultBuilder.build();
    }
}