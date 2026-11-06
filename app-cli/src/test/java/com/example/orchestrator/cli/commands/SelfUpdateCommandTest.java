package com.example.orchestrator.cli.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures self-update refuses to install a release that does not publish a
 * SHA256 checksum. No network calls are made: both cases have an empty asset
 * list and a null inline digest.
 */
class SelfUpdateCommandTest {

    @Test
    void refusesToResolveChecksumWhenReleasePublishesNone() throws Exception {
        SelfUpdateCommand command = new SelfUpdateCommand();

        Object release = newRelease(List.of());
        Object asset = newAsset("companion.jar", null);

        assertNull(invokeResolve(command, release, asset));
    }

    @Test
    void ignoresMalformedInlineChecksum() throws Exception {
        SelfUpdateCommand command = new SelfUpdateCommand();

        Object release = newRelease(List.of());
        Object asset = newAsset("companion.jar", "sk-or-v1-not-a-checksum");

        assertNull(invokeResolve(command, release, asset));
    }

    @Test
    void acceptsValidInlineChecksum() throws Exception {
        SelfUpdateCommand command = new SelfUpdateCommand();
        String digest = "a".repeat(64);

        Object release = newRelease(List.of());
        Object asset = newAsset("companion.jar", digest);

        Object resolved = invokeResolve(command, release, asset);
        assertNotNull(resolved);
        org.junit.jupiter.api.Assertions.assertEquals(digest, resolved);
    }

    @Test
    void rejectsPlainHttpUpdateUrls() {
        assertThrows(IllegalArgumentException.class,
            () -> SelfUpdateCommand.requireTrustedUpdateUri("http://github.com/owner/repo"));
    }

    @Test
    void rejectsUntrustedUpdateHosts() {
        assertThrows(IllegalArgumentException.class,
            () -> SelfUpdateCommand.requireTrustedUpdateUri("https://evil.example/companion.jar"));
        assertThrows(IllegalArgumentException.class,
            () -> SelfUpdateCommand.requireTrustedUpdateUri("https://github.com.evil.example/companion.jar"));
    }

    @Test
    void acceptsGitHubApiAndReleaseHosts() {
        assertTrue(SelfUpdateCommand.isTrustedUpdateUri(
            URI.create("https://api.github.com/repos/owner/repo/releases/latest")));
        assertTrue(SelfUpdateCommand.isTrustedUpdateUri(
            URI.create("https://github.com/owner/repo/releases/download/v1/companion.jar")));
        assertTrue(SelfUpdateCommand.isTrustedUpdateUri(
            URI.create("https://release-assets.githubusercontent.com/file")));
        assertFalse(SelfUpdateCommand.isTrustedUpdateUri(
            URI.create("http://api.github.com/repos/owner/repo/releases/latest")));
    }

    @Test
    void validatesRepositoryCoordinates() {
        assertTrue(SelfUpdateCommand.isValidRepository("owner/repo"));
        assertFalse(SelfUpdateCommand.isValidRepository("owner/repo/extra"));
        assertFalse(SelfUpdateCommand.isValidRepository("../repo"));
        assertFalse(SelfUpdateCommand.isValidRepository("owner"));
    }

    @Test
    void trustStoreRequiresPasswordAndFailsClosed(@TempDir Path tempDir) {
        Path store = tempDir.resolve("truststore.p12");
        assertThrows(IllegalArgumentException.class,
            () -> SelfUpdateCommand.buildTrustStoreContext(store, "PKCS12", null));
    }

    private Object newRelease(List<?> assets) throws Exception {
        Class<?> releaseInfo = Class.forName(SelfUpdateCommand.class.getName() + "$ReleaseInfo");
        Constructor<?> constructor = releaseInfo.getDeclaredConstructor(
            String.class, String.class, String.class, boolean.class, boolean.class, List.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance("v1.0.0", "v1.0.0", "", false, false, assets, "");
    }

    private Object newAsset(String name, String sha256) throws Exception {
        Class<?> releaseAsset = Class.forName(SelfUpdateCommand.class.getName() + "$ReleaseAsset");
        Constructor<?> constructor = releaseAsset.getDeclaredConstructor(
            String.class, long.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, 1L, "https://example.invalid/companion.jar", sha256, "application/java-archive");
    }

    private Object invokeResolve(SelfUpdateCommand command, Object release, Object asset) throws Exception {
        Class<?> releaseInfo = Class.forName(SelfUpdateCommand.class.getName() + "$ReleaseInfo");
        Class<?> releaseAsset = Class.forName(SelfUpdateCommand.class.getName() + "$ReleaseAsset");
        var method = SelfUpdateCommand.class.getDeclaredMethod(
            "resolveExpectedSha256", releaseInfo, releaseAsset);
        method.setAccessible(true);
        return method.invoke(command, release, asset);
    }
}
