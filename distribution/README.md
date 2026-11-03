# Distribution Assets (Early Access)

This directory holds packaging templates and a local validation script. **No
Homebrew tap, Scoop bucket, Docker image, or prebuilt release has been
published yet.** The supported install path is
[building from source](../README.md#quick-start-from-source).

## Files

| File | Purpose |
|------|---------|
| `homebrew/Formula/companion.rb` | Homebrew formula template (not published) |
| `scoop/bucket/companion.json` | Scoop manifest template (not published) |
| `test-distribution.sh` | Local validation script |

## What the release workflow does today

Tagging `v*` runs `.github/workflows/release.yml`, which:

1. Runs `mvn -B -ntp clean verify`.
2. Smoke-tests the CLI jar.
3. Attaches `companion.jar`, `companion.jar.sha256`, and `install.sh` to the
   GitHub release.

The workflow does **not** push to Homebrew or Scoop repositories. Those
templates require separate tap/bucket repositories and credentials.

## Validate the installer locally

```bash
mvn -B -ntp -pl app-cli -am package -DskipTests
./distribution/test-distribution.sh app-cli/target/app-cli-*-exec.jar 0.1.0-test
```

The installer verifies the SHA-256 checksum before installing. It requires Java
21+ and `curl`.

## Publishing a package manager later

Before publishing, create the tap/bucket repositories and replace the
placeholders in the templates:

- `version`
- `sha256`
- release URL (use the repository that actually hosts the release)

Only announce `brew install companion`, `scoop install companion`, Docker Hub
images, or release download commands after those assets exist.
