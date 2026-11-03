package com.example.orchestrator.repair;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RepairAttemptRepository {

    private final Map<String, RepairAttemptRecord> attempts = new ConcurrentHashMap<>();

    public void save(RepairAttemptRecord record) {
        String key = record.repairId() + "-" + record.attemptNumber();
        attempts.put(key, record);
    }

    public RepairAttemptRecord findByRepairAndAttempt(String repairId, int attemptNumber) {
        return attempts.get(repairId + "-" + attemptNumber);
    }

    public List<RepairAttemptRecord> findByRepairId(String repairId) {
        return attempts.values().stream()
            .filter(r -> r.repairId().equals(repairId))
            .sorted((a, b) -> Integer.compare(a.attemptNumber(), b.attemptNumber()))
            .toList();
    }

    public List<RepairAttemptRecord> findByTaskId(String taskId) {
        return attempts.values().stream()
            .filter(r -> r.taskId().equals(taskId))
            .sorted((a, b) -> Integer.compare(a.attemptNumber(), b.attemptNumber()))
            .toList();
    }

    public List<RepairAttemptRecord> findAll() {
        return List.copyOf(attempts.values());
    }
}