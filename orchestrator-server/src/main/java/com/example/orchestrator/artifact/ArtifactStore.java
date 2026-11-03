package com.example.orchestrator.artifact;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Artifact Store - persists job execution artifacts to both files and database.
 * 
 * H-16: Artifact Integrity Verification
 * - Computes SHA-256 checksum for all stored artifacts
 * - Verifies checksum on read to detect corruption/tampering
 * - Includes checksum in artifact metadata for client verification
 */
@Service("fileArtifactStore")
public class ArtifactStore {

    private final Path artifactsRoot;
    private final Map<String, ArtifactRecord> records = new ConcurrentHashMap<>();

    public ArtifactStore() throws IOException {
        this.artifactsRoot = Path.of("orchestrator-artifacts").toAbsolutePath();
        Files.createDirectories(artifactsRoot);
    }

    /**
     * Computes SHA-256 checksum of content.
     */
    private static String computeChecksum(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute checksum", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String storeArtifact(String taskId, String name, String content, String contentType) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        return storeArtifactBytes(taskId, name, contentBytes, contentType);
    }

    public String storeArtifactBytes(String taskId, String name, byte[] content, String contentType) throws IOException {
        String artifactId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = artifactId + "-" + name;
        Path filePath = artifactsRoot.resolve(taskId).resolve(fileName);
        
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);
        
        String checksum = computeChecksum(content);
        
        ArtifactRecord record = new ArtifactRecord(
            artifactId,
            taskId,
            name,
            fileName,
            contentType,
            content.length,
            filePath.toString(),
            Instant.now(),
            checksum
        );
        
        records.put(artifactId, record);
        return artifactId;
    }

    public ArtifactRecord getArtifact(String artifactId) {
        return records.get(artifactId);
    }

    public List<ArtifactRecord> getArtifactsForTask(String taskId) {
        return records.values().stream()
            .filter(r -> r.taskId().equals(taskId))
            .collect(Collectors.toList());
    }

    public byte[] readArtifactBytes(String artifactId) throws IOException {
        ArtifactRecord record = records.get(artifactId);
        if (record == null) {
            return null;
        }
        byte[] content = Files.readAllBytes(Path.of(record.path()));
        
        // H-16: Verify checksum on read
        String computedChecksum = computeChecksum(content);
        if (!record.checksum().equals(computedChecksum)) {
            throw new IllegalStateException(
                "Checksum verification failed for artifact " + record.artifactId() + 
                ": expected " + record.checksum() + ", computed " + computedChecksum
            );
        }
        
        return content;
    }

    public String readArtifactString(String artifactId) throws IOException {
        byte[] bytes = readArtifactBytes(artifactId);
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    public record ArtifactRecord(
        String artifactId,
        String taskId,
        String name,
        String fileName,
        String contentType,
        long sizeBytes,
        String path,
        Instant createdAt,
        String checksum
    ) {}
}