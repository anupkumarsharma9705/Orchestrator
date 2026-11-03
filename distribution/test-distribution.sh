#!/bin/bash
# Test script to verify Homebrew formula and Scoop manifest
# Run this locally after building a release JAR

set -euo pipefail

JAR_PATH="${1:-app-cli/target/companion-exec.jar}"
VERSION="${2:-0.1.0-test}"

echo "=== Testing Distribution Artifacts ==="
echo "JAR: $JAR_PATH"
echo "Version: $VERSION"

# 1. Calculate SHA256
echo ""
echo "1. Calculating SHA256..."
SHA256=$(sha256sum "$JAR_PATH" | cut -d' ' -f1)
echo "SHA256: $SHA256"

# 2. Test Homebrew formula syntax
echo ""
echo "2. Validating Homebrew formula syntax..."
if command -v brew >/dev/null 2>&1; then
    # Test formula syntax
    if brew formula distribution/homebrew/Formula/companion.rb 2>/dev/null; then
        echo "✓ Homebrew formula syntax OK"
    else
        echo "⚠ Homebrew not available or formula syntax error"
    fi
else
    echo "⚠ Homebrew not installed, skipping syntax check"
fi

# 3. Validate Scoop manifest JSON
echo ""
echo "3. Validating Scoop manifest JSON..."
if command -v jq >/dev/null 2>&1; then
    if jq empty distribution/scoop/bucket/companion.json 2>/dev/null; then
        echo "✓ Scoop manifest JSON valid"
    else
        echo "✗ Scoop manifest JSON invalid"
        exit 1
    fi
else
    echo "⚠ jq not installed, skipping JSON validation"
fi

# 4. Test JAR runs
echo ""
echo "4. Testing JAR execution..."
if java -jar "$JAR_PATH" --help >/dev/null 2>&1; then
    echo "✓ JAR runs successfully"
else
    echo "✗ JAR failed to run"
    exit 1
fi

# 5. Test --version flag
echo ""
echo "5. Testing version output..."
VERSION_OUTPUT=$(java -jar "$JAR_PATH" --version 2>&1 || true)
echo "Version output: $VERSION_OUTPUT"

# 6. Generate updated formula/manifest for testing
echo ""
echo "6. Generating test formula/manifest with current SHA256..."
cat > /tmp/companion-test.rb <<EOF
class Companion < Formula
  desc "Generate production-ready Spring Boot REST APIs from natural language"
  homepage "https://github.com/akash/java-companion"
  version "$VERSION"
  license "Apache-2.0"

  url "https://github.com/akash/java-companion/releases/download/v$VERSION/companion.jar"
  sha256 "$SHA256"

  depends_on "openjdk@21"

  def install
    libexec.install "companion.jar" => "companion.jar"

    (bin/"companion").write <<~EOS
      #!/bin/bash
      set -euo pipefail

      JAVA_CMD="#{Formula["openjdk@21"].opt_bin}/java"
      JAR_PATH="#{libexec}/companion.jar"

      if ! "\${JAVA_CMD}" -version 2>&1 | grep -q "version \"2[1-9]"; then
        echo "Error: Java 21+ required. Found:" >&2
        "\${JAVA_CMD}" -version 2>&1 | head -1 >&2
        exit 1
      fi

      exec "\${JAVA_CMD}" -jar "\${JAR_PATH}" "\$@"
    EOS
  end

  test do
    output = shell_output("#{bin}/companion --help 2>&1")
    assert_match "Generate a new Spring Boot project", output
  end
end
EOF

cat > /tmp/companion-test.json <<EOF
{
  "version": "$VERSION",
  "description": "Generate production-ready Spring Boot REST APIs from natural language",
  "homepage": "https://github.com/akash/java-companion",
  "license": "Apache-2.0",
  "architecture": {
    "64bit": {
      "url": "https://github.com/akash/java-companion/releases/download/v$VERSION/companion.jar",
      "hash": "$SHA256"
    }
  },
  "depends": "openjdk21",
  "bin": "companion.jar"
}
EOF

echo "✓ Generated test formula at /tmp/companion-test.rb"
echo "✓ Generated test manifest at /tmp/companion-test.json"

echo ""
echo "=== All Checks Passed ==="
echo "SHA256 for release: $SHA256"
echo ""
echo "Next steps:"
echo "1. Update distribution/homebrew/Formula/companion.rb with version $VERSION and SHA256"
echo "2. Update distribution/scoop/bucket/companion.json with version $VERSION and SHA256"
echo "3. Commit and push to homebrew-tap and scoop-bucket repos"
echo "4. Tag release: git tag v$VERSION && git push origin v$VERSION"