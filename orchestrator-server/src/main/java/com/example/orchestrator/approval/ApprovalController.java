package com.example.orchestrator.approval;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/tasks")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "Approved via API") : "Approved via API";
        
        if (!approvalService.isPending(id)) {
            return ResponseEntity.notFound().build();
        }
        
        approvalService.submitApproval(
            com.example.orchestrator.approval.ApprovalDecision.approved(id, "api", comment)
        );
        
        return ResponseEntity.ok(Map.of("status", "approved", "taskId", id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String comment = body != null ? body.getOrDefault("comment", "Rejected via API") : "Rejected via API";
        
        if (!approvalService.isPending(id)) {
            return ResponseEntity.notFound().build();
        }
        
        approvalService.submitRejection(id, "api", comment);
        
        return ResponseEntity.ok(Map.of("status", "rejected", "taskId", id));
    }
}