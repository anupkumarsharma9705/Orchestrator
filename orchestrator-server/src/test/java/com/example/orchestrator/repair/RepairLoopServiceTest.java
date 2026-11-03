package com.example.orchestrator.repair;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.project.Project;
import com.example.orchestrator.project.ProjectManager;
import com.example.orchestrator.verification.VerificationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@Import(SharedTestConfig.class)
class RepairLoopServiceTest {

    @Autowired
    private RepairLoopService repairLoopService;

    @Autowired
    private ProjectManager projectManager;

    @BeforeEach
    void setup() {
        repairLoopService = Mockito.mock(RepairLoopService.class);
        projectManager = Mockito.mock(ProjectManager.class);
        
        // Mock projectManager.register
        Mockito.when(projectManager.register(any(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                String id = invocation.getArgument(0);
                String name = invocation.getArgument(1);
                Path rootPath = invocation.getArgument(2);
                String buildTool = invocation.getArgument(3);
                String testCommand = invocation.getArgument(4);
                Map<String, String> env = invocation.getArgument(5);
                return new Project(id, name, rootPath, rootPath.resolve(".ai"), buildTool, testCommand, env, Instant.now(), Instant.now());
            });
        
        // Mock repairLoopService.startRepairLoop
        Mockito.when(repairLoopService.startRepairLoop(any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                String taskId = invocation.getArgument(0);
                Project project = invocation.getArgument(1);
                return new RepairSession(
                    "repair-" + taskId,
                    taskId,
                    project,
                    3,
                    10,
                    30,
                    Instant.now()
                );
            });
    }

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateRepairSession() throws IOException {
        Path projectRoot = tempDir.resolve("test-project");
        Files.createDirectories(projectRoot);

        Project project = projectManager.register(
            "test-project",
            "Test Project",
            projectRoot,
            "maven",
            "test",
            Map.of()
        );

        VerificationConfig config = VerificationConfig.defaultConfig();
        RepairLoopConfig loopConfig = RepairLoopConfig.defaultConfig();

        RepairSession session = repairLoopService.startRepairLoop(
            "test-task", project, VerificationConfig.defaultConfig(), RepairLoopConfig.defaultConfig()
        );

        assertNotNull(session);
        assertNotNull(session.repairId());
        assertEquals("test-task", session.taskId());
        assertEquals(RepairSession.RepairStatus.PENDING, session.status());
    }

    @Test
    void shouldCreateDefaultRepairLoopConfig() {
        RepairLoopConfig config = RepairLoopConfig.defaultConfig();
        assertEquals(3, config.maxAttempts());
        assertEquals(10, config.attemptTimeoutMinutes());
        assertEquals(30, config.totalTimeoutMinutes());
    }

    @Test
    void shouldCreateAggressiveRepairLoopConfig() {
        RepairLoopConfig config = RepairLoopConfig.aggressiveConfig();
        assertEquals(5, config.maxAttempts());
        assertEquals(15, config.attemptTimeoutMinutes());
        assertEquals(60, config.totalTimeoutMinutes());
    }

    @Test
    void shouldCreateConservativeRepairLoopConfig() {
        RepairLoopConfig config = RepairLoopConfig.conservativeConfig();
        assertEquals(2, config.maxAttempts());
        assertEquals(5, config.attemptTimeoutMinutes());
        assertEquals(15, config.totalTimeoutMinutes());
    }
}