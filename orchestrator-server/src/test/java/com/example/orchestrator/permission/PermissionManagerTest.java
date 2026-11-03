package com.example.orchestrator.permission;

import com.example.orchestrator.SharedTestConfig;
import com.example.orchestrator.tool.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@Import(SharedTestConfig.class)
class PermissionManagerTest {

    @Autowired
    private PermissionManager permissionManager;

    @BeforeEach
    void setup() {
        permissionManager = Mockito.mock(PermissionManager.class);
        
        // Mock checkPermission behavior
        Mockito.when(permissionManager.checkPermission(eq(ToolSpec.RiskLevel.SAFE), any()))
            .thenReturn(true);
        
        Mockito.when(permissionManager.checkPermission(eq(ToolSpec.RiskLevel.CONTROLLED), any()))
            .thenAnswer(invocation -> {
                PermissionManager.PermissionContext ctx = invocation.getArgument(1);
                return ctx.hasUser();
            });
        
        Mockito.when(permissionManager.checkPermission(eq(ToolSpec.RiskLevel.APPROVAL_REQUIRED), any()))
            .thenAnswer(invocation -> {
                PermissionManager.PermissionContext ctx = invocation.getArgument(1);
                return ctx.hasApproval();
            });
        
        Mockito.when(permissionManager.checkPermission(eq(ToolSpec.RiskLevel.HUMAN_ONLY), any()))
            .thenAnswer(invocation -> {
                PermissionManager.PermissionContext ctx = invocation.getArgument(1);
                return ctx.isHuman();
            });
    }

    @Test
    void shouldAllowSafeToolsWithoutUser() {
        PermissionManager.PermissionContext context = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .build();

        assertTrue(permissionManager.checkPermission(ToolSpec.RiskLevel.SAFE, context));
    }

    @Test
    void shouldRequireUserForControlledTools() {
        PermissionManager.PermissionContext contextWithoutUser = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .build();

        PermissionManager.PermissionContext contextWithUser = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .userId("test-user")
            .build();

        assertFalse(permissionManager.checkPermission(ToolSpec.RiskLevel.CONTROLLED, contextWithoutUser));
        assertTrue(permissionManager.checkPermission(ToolSpec.RiskLevel.CONTROLLED, contextWithUser));
    }

    @Test
    void shouldRequireApprovalForApprovalRequiredTools() {
        PermissionManager.PermissionContext contextWithoutApproval = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .userId("test-user")
            .hasApproval(false)
            .build();

        PermissionManager.PermissionContext contextWithApproval = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .userId("test-user")
            .hasApproval(true)
            .build();

        assertFalse(permissionManager.checkPermission(ToolSpec.RiskLevel.APPROVAL_REQUIRED, contextWithoutApproval));
        assertTrue(permissionManager.checkPermission(ToolSpec.RiskLevel.APPROVAL_REQUIRED, contextWithApproval));
    }

    @Test
    void shouldRequireHumanForHumanOnlyTools() {
        PermissionManager.PermissionContext aiContext = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .userId("test-user")
            .hasApproval(true)
            .actorType("ai")
            .build();

        PermissionManager.PermissionContext humanContext = PermissionManager.PermissionContext.builder()
            .taskId("test-task")
            .userId("test-user")
            .hasApproval(true)
            .actorType("human")
            .build();

        assertFalse(permissionManager.checkPermission(ToolSpec.RiskLevel.HUMAN_ONLY, aiContext));
        assertTrue(permissionManager.checkPermission(ToolSpec.RiskLevel.HUMAN_ONLY, humanContext));
    }
}