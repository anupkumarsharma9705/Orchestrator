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
        String safeTaskId = sanitizePathSegment(taskId, "task");
        String safeName = sanitizeFileName(name);
        String fileName = artifactId + "-" + safeName;
        Path filePath = artifactsRoot.resolve(safeTaskId).resolve(fileName).normalize();
        if (!filePath.startsWith(artifactsRoot)) {
            throw new IOException("Artifact path escaped the artifact root");
        }

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content);
        
        String checksum = computeChecksum(content);

        // Persist a path relative to the artifact root; absolute host paths are
        // never exposed through the API or stored in metadata.
        String relativePath = artifactsRoot.relativize(filePath).toString().replace('\\', '/');

        ArtifactRecord record = new ArtifactRecord(
            artifactId,
            safeTaskId,
            safeName,
            fileName,
            contentType,
            content.length,
            relativePath,
            Instant.now(),
            checksum
        );
        
        records.put(artifactId, record);
        return artifactId;
    }

    private static String sanitizePathSegment(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "artifact";
        }
        String baseName = Path.of(name).getFileName() == null
            ? "artifact"
            : Path.of(name).getFileName().toString();
        String sanitized = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")
            ? "artifact"
            : sanitized;
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
        Path artifactPath = artifactsRoot.resolve(record.path()).normalize();
        if (!artifactPath.startsWith(artifactsRoot)) {
            throw new IOException("Artifact path escaped the artifact root");
        }
        byte[] content = Files.readAllBytes(artifactPath);
        
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