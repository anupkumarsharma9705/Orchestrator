package com.example.orchestrator.integration;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.approval.ApprovalDecision;
import com.example.orchestrator.approval.ApprovalService;
import com.example.orchestrator.execution.ExecutionEngine;
import com.example.orchestrator.execution.ExecutionState;
import com.example.orchestrator.execution.ExecutionStatus;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.project.ProjectManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Import(SharedTestConfig.class)
class ExecutionIntegrationTest {

    @Autowired
    private ExecutionEngine executionEngine;

    @Autowired
    private ProjectManager projectManager;

    @Autowired
    private ApprovalService approvalService;

    @TempDir
    Path tempDir;

    @Test
    @Disabled("Requires OPENROUTER_API_KEY to be configured")
    void shouldExecuteReadOnlyTaskEndToEnd() throws IOException {
        // Create a simple test project
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot.resolve("src/main/java/com/example"));

        Files.writeString(projectRoot.resolve("src/main/java/com/example/Controller.java"), """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api")
            public class Controller {
                @GetMapping("/hello")
                public String hello() {
                    return "Hello World";
                }
            }
            """);

        Files.writeString(projectRoot.resolve("pom.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                </parent>
                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """);

        // Register project
        Project project = projectManager.register(
            "test-project",
            "Test Project",
            projectRoot,
            "maven",
            "test",
            Map.of()
        );

        // Submit a read-only task
        String taskId = executionEngine.submitTask(
            project.id(),
            "List all Java files in the project",
            "test-user"
        );

        // Wait for task to reach WAITING_APPROVAL
        ExecutionState state = waitForState(taskId, ExecutionStatus.WAITING_APPROVAL, 60000);
        assertNotNull(state);
        assertEquals(ExecutionStatus.WAITING_APPROVAL, state.status());

        // Approve the task
        approvalService.submitApproval(ApprovalDecision.approved(taskId, "test-user", "Approved for testing"));

        // Wait for completion
        ExecutionState finalState = waitForState(taskId, ExecutionStatus.COMPLETED, 120000);
        assertNotNull(finalState);
        assertEquals(ExecutionStatus.COMPLETED, finalState.status());
        assertNotNull(finalState.toolResults());
        assertFalse(finalState.toolResults().isEmpty());
    }

    private ExecutionState waitForState(String taskId, ExecutionStatus expectedStatus, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            ExecutionState state = executionEngine.getState(taskId);
            if (state != null && state.status() == expectedStatus) {
                return state;
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return null;
    }
}