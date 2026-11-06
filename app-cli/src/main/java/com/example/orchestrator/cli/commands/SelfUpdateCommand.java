package com.example.orchestrator.cli.commands;

import com.example.orchestrator.cli.CompanionCli;
import com.example.orchestrator.cli.config.CliConfig;
import com.example.orchestrator.cli.config.ConfigManager;
import com.example.orchestrator.cli.output.OutputFormatter;
import com.example.orchestrator.cli.util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Self-update command - checks for updates and upgrades the CLI binary.
 * Uses GitHub Releases API to find latest version, downloads JAR, verifies checksum,
 * and atomically replaces the running JAR.
 */
@Command(
    name = "self-update",
    description = "Check for updates and upgrade the CLI to the latest version",
    mixinStandardHelpOptions = true
)
public class SelfUpdateCommand implements Callable<Integer> {

    @Option(names = {"--check-only"}, description = "Only check for updates, don't install")
    private boolean checkOnly;

    @Option(names = {"--dry-run"}, description = "Show what would be done without making changes")
    private boolean dryRun;

    @Option(names = {"--force"}, description = "Force update even if already on latest version")
    private boolean force;

    @Option(names = {"--prerelease"}, description = "Include pre-release versions (alpha, beta, rc)")
    private boolean includePrerelease;

    @Option(names = {"--repo"}, description = "GitHub repository (owner/repo)", defaultValue = "akash/java-companion")
    private String repository;

    @Option(names = {"--trust-store"},
        description = "PKCS12/JKS trust store used to verify GitHub TLS certificates")
    private Path trustStore;

    @Option(names = {"--trust-store-type"},
        description = "Trust store type (PKCS12 or JKS)",
        defaultValue = "PKCS12")
    private String trustStoreType;

    @ParentCommand
    private CompanionCli parent;

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String RELEASES_API = "/repos/%s/releases";
    private static final String LATEST_RELEASE_API = "/repos/%s/releases/latest";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern REPOSITORY_PATTERN =
        Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?/[A-Za-z0-9_.-]+$");
    private static final Set<String> TRUSTED_UPDATE_HOSTS =
        Set.of("api.github.com", "github.com");
    private static final String TRUST_STORE_PASSWORD_PROPERTY =
        "companion.update.trust-store-password";
    private static final String TRUST_STORE_PASSWORD_ENV =
        "COMPANION_UPDATE_TRUST_STORE_PASSWORD";

    @Override
    public Integer call() throws Exception {
        CliConfig config = new ConfigManager().load();
        OutputFormatter formatter = new OutputFormatter(config.outputFormat());

        if (!isValidRepository(repository)) {
            System.err.println(Ansi.error(" Invalid repository (expected owner/repo): " + repository));
            return 1;
        }
        if (trustStore != null && trustStorePassword() == null) {
            System.err.println(Ansi.error(
                " A trust store password is required. Set " + TRUST_STORE_PASSWORD_PROPERTY
                    + " or " + TRUST_STORE_PASSWORD_ENV + "."));
            return 1;
        }

        System.out.println(Ansi.info(" Checking for updates..."));
        System.out.println(Ansi.dim(" Repository: " + repository));
        System.out.println(Ansi.dim(" Current version: " + getCurrentVersion()));

        // Fetch latest release
        ReleaseInfo latest = fetchLatestRelease();
        if (latest == null) {
            System.err.println(Ansi.error(" Failed to fetch release information"));
            return 1;
        }

        String latestVersion = latest.tagName();
        String currentVersion = getCurrentVersion();

        System.out.println(Ansi.bold(" Latest version: " + latestVersion));
        System.out.println(Ansi.bold(" Current version: " + currentVersion));

        // Compare versions
        int comparison = compareVersions(currentVersion, latestVersion);
        if (comparison >= 0 && !force) {
            System.out.println(Ansi.success(" Already on the latest version (" + currentVersion + ")"));
            System.out.println(Ansi.dim(" Use --force to reinstall or --prerelease to check for pre-releases"));
            return 0;
        }

        if (comparison < 0) {
            System.out.println(Ansi.warning(" Update available: " + currentVersion + " → " + latestVersion));
        } else if (force) {
            System.out.println(Ansi.warning(" Forcing reinstall of version " + latestVersion));
        }

        if (checkOnly) {
            System.out.println(Ansi.info(" Check-only mode: not installing"));
            return 0;
        }

        // Find JAR asset
        ReleaseAsset jarAsset = findJarAsset(latest);
        if (jarAsset == null) {
            System.err.println(Ansi.error(" No JAR asset found in release " + latestVersion));
            return 1;
        }

        System.out.println(Ansi.info(" Downloading " + jarAsset.name() + " (" + formatBytes(jarAsset.size()) + ")..."));

        // Self-update replaces the running binary. It must never install an
        // artifact whose integrity cannot be checked, so a published SHA256 is
        // mandatory.
        String expectedSha256 = resolveExpectedSha256(latest, jarAsset);
        if (expectedSha256 == null) {
            System.err.println(Ansi.error(
                " No SHA256 checksum published for " + jarAsset.name()
                    + " in release " + latestVersion));
            System.err.println(Ansi.dim(
                " Refusing to self-update without integrity verification. Publish <jar>.sha256 or checksums.txt."));
            return 1;
        }

        if (dryRun) {
            System.out.println(Ansi.info(" Dry-run mode: would download and install " + latestVersion));
            System.out.println(Ansi.dim(" URL: " + jarAsset.browserDownloadUrl()));
            System.out.println(Ansi.dim(" SHA256: " + expectedSha256));
            return 0;
        }

        // Download and verify
        Path tempJar = downloadAndVerify(jarAsset, expectedSha256);
        if (tempJar == null) {
            return 1;
        }

        // Install
        return installUpdate(tempJar);
    }

    private HttpClient buildHttpClient(Duration connectTimeout) throws IOException {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            // NORMAL never downgrades an HTTPS request to HTTP.
            .followRedirects(HttpClient.Redirect.NORMAL);

        if (trustStore != null) {
            String password = trustStorePassword();
            if (password == null) {
                throw new IOException(
                    "A trust store password is required. Set " + TRUST_STORE_PASSWORD_PROPERTY
                        + " or " + TRUST_STORE_PASSWORD_ENV + ".");
            }
            builder.sslContext(buildTrustStoreContext(trustStore, trustStoreType, password));
        }
        // When no trust store is supplied, HttpClient uses the JVM default
        // trust store and hostname verification. Certificate validation is
        // never disabled.
        return builder.build();
    }

    private String trustStorePassword() {
        String password = System.getProperty(TRUST_STORE_PASSWORD_PROPERTY);
        if (password == null || password.isEmpty()) {
            password = System.getenv(TRUST_STORE_PASSWORD_ENV);
        }
        return password == null || password.isEmpty() ? null : password;
    }

    /**
     * Builds an SSL context from an explicit trust store. The store replaces
     * the JVM default, so it must contain the CA chain used by GitHub (or the
     * CA of a compatible enterprise mirror). There is deliberately no
     * trust-all fallback.
     */
    static SSLContext buildTrustStoreContext(Path store, String type, String password) throws IOException {
        if (store == null) {
            throw new IllegalArgumentException("Trust store path is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("Trust store password is required");
        }
        String storeType = type == null || type.isBlank() ? KeyStore.getDefaultType() : type;
        try (InputStream input = Files.newInputStream(store)) {
            KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(input, password.toCharArray());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to load update trust store: " + store, e);
        }
    }

    static boolean isValidRepository(String value) {
        return value != null && REPOSITORY_PATTERN.matcher(value).matches();
    }

    static URI requireTrustedUpdateUri(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Update URL is empty");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid update URL: " + raw, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Update URL must use HTTPS: " + raw);
        }
        if (!isTrustedUpdateHost(uri.getHost())) {
            throw new IllegalArgumentException("Update URL host is not trusted: " + uri.getHost());
        }
        return uri;
    }

    static boolean isTrustedUpdateUri(URI uri) {
        return uri != null
            && "https".equalsIgnoreCase(uri.getScheme())
            && isTrustedUpdateHost(uri.getHost());
    }

    static boolean isTrustedUpdateHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return TRUSTED_UPDATE_HOSTS.contains(lower)
            || lower.endsWith(".githubusercontent.com");
    }

    private String getCurrentVersion() {
        // Try to get from Maven properties or manifest
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null || version.isEmpty()) {
            version = "0.0.1-SNAPSHOT";
        }
        return version;
    }

    private ReleaseInfo fetchLatestRelease() throws IOException, InterruptedException {
        String url;
        if (includePrerelease) {
            // Fetch all releases and find latest (including prereleases)
            url = String.format(GITHUB_API_BASE + RELEASES_API + "?per_page=10", repository);
        } else {
            url = String.format(GITHUB_API_BASE + LATEST_RELEASE_API, repository);
        }

        URI uri = requireTrustedUpdateUri(url);
        HttpClient client = buildHttpClient(Duration.ofSeconds(10));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Companion-CLI-Updater")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (!isTrustedUpdateUri(response.uri())) {
            System.err.println(Ansi.error(" Update API redirected to an untrusted host; refusing response"));
            return null;
        }
        if (response.statusCode() == 404) {
            System.err.println(Ansi.error(" Repository not found: " + repository));
            return null;
        }
        if (response.statusCode() != 200) {
            System.err.println(Ansi.error(" GitHub API error: " + response.statusCode() + " - " + response.body()));
            return null;
        }

        if (includePrerelease) {
            // Parse array and find latest non-draft
            JsonNode releases = MAPPER.readTree(response.body());
            for (JsonNode release : releases) {
                if (!release.get("draft").asBoolean() && 
                    (!release.get("prerelease").asBoolean() || includePrerelease)) {
                    return MAPPER.treeToValue(release, ReleaseInfo.class);
                }
            }
            return null;
        } else {
            return MAPPER.readValue(response.body(), ReleaseInfo.class);
        }
    }

    private ReleaseAsset findJarAsset(ReleaseInfo release) {
        if (release.assets() == null || release.assets().isEmpty()) {
            return null;
        }
        
        // Look for companion.jar or companion-*.jar
        for (ReleaseAsset asset : release.assets()) {
            String name = asset.name().toLowerCase();
            if (name.equals("companion.jar") || 
                name.matches("companion[-_].*\\.jar$") ||
                name.endsWith("-exec.jar")) {
                return asset;
            }
        }
        
        // Fallback: first .jar asset
        for (ReleaseAsset asset : release.assets()) {
            if (asset.name().toLowerCase().endsWith(".jar")) {
                return asset;
            }
        }
        
        return null;
    }

    /**
     * Resolves the expected SHA256 for the JAR. GitHub's release asset JSON does
     * not carry a checksum, so the value normally comes from a sidecar asset:
     * {@code <jar>.sha256}, {@code checksums.txt} or {@code SHA256SUMS}.
     *
     * @return a 64-character hex digest, or {@code null} when none is published
     */
    private String resolveExpectedSha256(ReleaseInfo release, ReleaseAsset jarAsset)
            throws IOException, InterruptedException {
        if (jarAsset.sha256() != null && !jarAsset.sha256().isBlank()) {
            String inline = normaliseSha256(jarAsset.sha256());
            if (inline != null) {
                return inline;
            }
        }

        ReleaseAsset checksumAsset = findChecksumAsset(release, jarAsset.name());
        if (checksumAsset == null) {
            return null;
        }

        String content = downloadText(checksumAsset.browserDownloadUrl());
        if (content == null) {
            return null;
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // Accept "<digest>", "<digest>  <file>" and "*<file>" forms.
            String[] parts = trimmed.split("\\s+", 2);
            String digest = normaliseSha256(parts[0]);
            if (digest == null) {
                continue;
            }
            if (parts.length == 1) {
                return digest;
            }
            String listedFile = parts[1].trim().replaceFirst("^\\*", "");
            if (listedFile.equals(jarAsset.name())) {
                return digest;
            }
        }
        return null;
    }

    private ReleaseAsset findChecksumAsset(ReleaseInfo release, String jarName) {
        if (release.assets() == null) {
            return null;
        }
        String[] exactNames = {
            jarName + ".sha256",
            jarName + ".sha256.txt",
            "SHA256SUMS",
            "SHA256SUMS.txt",
            "checksums.txt",
            "checksums.sha256"
        };
        for (String expected : exactNames) {
            for (ReleaseAsset asset : release.assets()) {
                if (asset.name() != null && asset.name().equalsIgnoreCase(expected)) {
                    return asset;
                }
            }
        }
        return null;
    }

    private String normaliseSha256(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.startsWith("\uFEFF")) {
            candidate = candidate.substring(1);
        }
        return candidate.matches("^[a-fA-F0-9]{64}$") ? candidate.toLowerCase() : null;
    }

    private String downloadText(String url) throws IOException, InterruptedException {
        URI uri = requireTrustedUpdateUri(url);
        HttpClient client = buildHttpClient(Duration.ofSeconds(30));
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "text/plain")
            .header("User-Agent", "Companion-CLI-Updater")
            .timeout(Duration.ofMinutes(1))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (!isTrustedUpdateUri(response.uri())) {
            System.err.println(Ansi.error(" Checksum download redirected to an untrusted host"));
            return null;
        }
        if (response.statusCode() != 200) {
            System.err.println(Ansi.error(
                " Failed to download checksum file: HTTP " + response.statusCode()));
            return null;
        }
        return response.body();
    }

    private Path downloadAndVerify(ReleaseAsset asset, String expectedSha256) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("companion-update-");
        Path tempJar = tempDir.resolve("companion-new.jar");

        URI downloadUri = requireTrustedUpdateUri(asset.browserDownloadUrl());
        HttpClient client = buildHttpClient(Duration.ofSeconds(30));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(downloadUri)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "Companion-CLI-Updater")
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build();

        System.out.print(Ansi.dim(" Downloading... "));
        
        HttpResponse<Path> response = client.send(
            request, 
            HttpResponse.BodyHandlers.ofFile(tempJar)
        );

        if (!isTrustedUpdateUri(response.uri())) {
            System.out.println(Ansi.error(" Failed: download redirected to an untrusted host"));
            Files.deleteIfExists(tempJar);
            return null;
        }

        if (response.statusCode() != 200) {
            System.out.println(Ansi.error(" Failed: HTTP " + response.statusCode()));
            Files.deleteIfExists(tempJar);
            return null;
        }

        System.out.println(Ansi.success(" Done"));

        // Verify SHA256. The caller guarantees expectedSha256 is present.
        System.out.print(Ansi.dim(" Verifying SHA256... "));
        String actualSha256 = calculateSha256(tempJar);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            System.out.println(Ansi.error(" FAILED"));
            System.err.println(Ansi.error(" Checksum mismatch - refusing to install the update!"));
            System.err.println(Ansi.dim(" Expected: " + expectedSha256));
            System.err.println(Ansi.dim(" Actual:   " + actualSha256));
            Files.deleteIfExists(tempJar);
            return null;
        }
        System.out.println(Ansi.success(" Verified"));

        return tempJar;
    }

    private int installUpdate(Path newJar) {
        try {
            // Find current JAR location
            Path currentJar = findCurrentJar();
            if (currentJar == null) {
                System.err.println(Ansi.error(" Could not locate current JAR"));
                return 1;
            }

            System.out.println(Ansi.info(" Current JAR: " + currentJar));
            System.out.println(Ansi.info(" Installing update..."));

            // Create backup
            Path backupJar = currentJar.resolveSibling(currentJar.getFileName() + ".backup");
            System.out.print(Ansi.dim(" Creating backup... "));
            Files.copy(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(Ansi.success(" Done"));

            try {
                // Atomic replace
                System.out.print(Ansi.dim(" Installing new version... "));
                Files.move(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                System.out.println(Ansi.success(" Done"));

                // Verify installation
                System.out.print(Ansi.dim(" Verifying installation... "));
                ProcessBuilder verify = new ProcessBuilder("java", "-jar", currentJar.toString(), "--version");
                Process verifyProcess = verify.start();
                boolean finished = verifyProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && verifyProcess.exitValue() == 0) {
                    System.out.println(Ansi.success(" Verified"));
                } else {
                    throw new IOException("Verification failed");
                }

                // Clean up backup
                Files.deleteIfExists(backupJar);
                Files.deleteIfExists(newJar.getParent());

                System.out.println();
                System.out.println(Ansi.success(" ✓ Successfully updated to latest version!"));
                System.out.println(Ansi.dim(" Run 'companion --version' to confirm"));

                return 0;

            } catch (Exception e) {
                System.out.println(Ansi.error(" Failed"));
                System.err.println(Ansi.error(" Installation failed: " + e.getMessage()));
                
                // Rollback
                System.out.print(Ansi.dim(" Rolling back... "));
                try {
                    Files.move(backupJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(Ansi.success(" Rolled back"));
                } catch (IOException rollbackEx) {
                    System.out.println(Ansi.error(" Rollback failed: " + rollbackEx.getMessage()));
                    System.err.println(Ansi.error(" Manual intervention required! Backup at: " + backupJar));
                }
                return 1;
            }

        } catch (IOException e) {
            System.err.println(Ansi.error(" Installation failed: " + e.getMessage()));
            return 1;
        }
    }

    private Path findCurrentJar() {
        // Try multiple strategies to find the running JAR
        
        // 1. From system property (set by launch script)
        String jarPath = System.getProperty("companion.jar.path");
        if (jarPath != null) {
            Path p = Path.of(jarPath);
            if (Files.exists(p)) return p;
        }

        // 2. From class location
        String classPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        Path classPathObj = Path.of(classPath);
        if (Files.exists(classPathObj) && classPathObj.toString().endsWith(".jar")) {
            return classPathObj;
        }

        // 3. Check common locations
        Path[] candidates = {
            Path.of(System.getProperty("user.dir"), "companion.jar"),
            Path.of(System.getProperty("user.home"), ".local", "bin", "companion.jar"),
            Path.of("/usr/local/bin", "companion.jar"),
            Path.of("/opt/companion", "companion.jar")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private int compareVersions(String current, String latest) {
        // Simple semantic version comparison
        String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");
        String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");
        
        int maxLen = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < maxLen; i++) {
            int curr = i < currentParts.length ? parseInt(currentParts[i]) : 0;
            int lat = i < latestParts.length ? parseInt(latestParts[i]) : 0;
            if (curr != lat) {
                return Integer.compare(curr, lat);
            }
        }
        return 0;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    // DTOs for GitHub API
    private record ReleaseInfo(
        String tagName,
        String name,
        String body,
        boolean draft,
        boolean prerelease,
        java.util.List<ReleaseAsset> assets,
        String htmlUrl
    ) {}

    private record ReleaseAsset(
        String name,
        long size,
        String browserDownloadUrl,
        String sha256,
        String contentType
    ) {}
}