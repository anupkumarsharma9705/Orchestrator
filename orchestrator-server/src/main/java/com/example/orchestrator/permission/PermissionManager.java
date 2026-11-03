package com.example.orchestrator.permission;

import com.example.orchestrator.tool.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Service
public class PermissionManager {

    private final Map<ToolSpec.RiskLevel, Predicate<PermissionContext>> policies = new ConcurrentHashMap<>();

    public PermissionManager() {
        // SAFE - always allowed
        policies.put(ToolSpec.RiskLevel.SAFE, ctx -> true);
        
        // CONTROLLED - requires authenticated user
        policies.put(ToolSpec.RiskLevel.CONTROLLED, ctx -> ctx.hasUser());
        
        // APPROVAL_REQUIRED - requires explicit approval for this task
        policies.put(ToolSpec.RiskLevel.APPROVAL_REQUIRED, ctx -> ctx.hasApproval());
        
        // HUMAN_ONLY - requires human actor (not AI)
        policies.put(ToolSpec.RiskLevel.HUMAN_ONLY, ctx -> ctx.isHuman());
    }

    public boolean checkPermission(ToolSpec.RiskLevel riskLevel, PermissionContext context) {
        Predicate<PermissionContext> policy = policies.get(riskLevel);
        if (policy == null) {
            return false; // Unknown risk level = deny
        }
        return policy.test(context);
    }

    public void registerPolicy(ToolSpec.RiskLevel riskLevel, Predicate<PermissionContext> policy) {
        policies.put(riskLevel, policy);
    }

    public static class PermissionContext {
        private final String userId;
        private final String taskId;
        private final boolean hasApproval;
        private final boolean isHuman;
        private final String actorType; // "human" or "ai"

        private PermissionContext(Builder builder) {
            this.userId = builder.userId;
            this.taskId = builder.taskId;
            this.hasApproval = builder.hasApproval;
            this.isHuman = builder.isHuman;
            this.actorType = builder.actorType;
        }

        public boolean hasUser() {
            return userId != null && !userId.isBlank();
        }

        public boolean hasApproval() {
            return hasApproval;
        }

        public boolean isHuman() {
            return isHuman;
        }

        public String getUserId() { return userId; }
        public String getTaskId() { return taskId; }
        public String getActorType() { return actorType; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String userId;
            private String taskId;
            private boolean hasApproval;
            private boolean isHuman;
            private String actorType = "ai";

            public Builder userId(String userId) { this.userId = userId; return this; }
            public Builder taskId(String taskId) { this.taskId = taskId; return this; }
            public Builder hasApproval(boolean hasApproval) { this.hasApproval = hasApproval; return this; }
            public Builder isHuman(boolean isHuman) { this.isHuman = isHuman; return this; }
            public Builder actorType(String actorType) { this.actorType = actorType; return this; }

            public PermissionContext build() {
                this.isHuman = "human".equalsIgnoreCase(actorType);
                return new PermissionContext(this);
            }
        }
    }
}