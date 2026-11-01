#!/bin/bash
# Java Companion Orchestrator - Install Script
# Usage: curl -sSL https://github.com/owner/java-companion/releases/latest/download/install.sh | bash

set -euo pipefail

REPO="owner/java-companion"
INSTALL_DIR="${HOME}/.local/bin"
JAR_NAME="companion.jar"

# Get latest release version
LATEST_VERSION=$(curl -s "https://api.github.com/repos/${REPO}/releases/latest" | grep '"tag_name"' | cut -d'"' -f4)
if [ -z "${LATEST_VERSION}" ]; then
    echo "Error: Could not determine latest version"
    exit 1
fi

echo "Installing Java Companion Orchestrator ${LATEST_VERSION}..."

# Create install directory
mkdir -p "${INSTALL_DIR}"

# Download JAR
JAR_URL="https://github.com/${REPO}/releases/download/${LATEST_VERSION}/companion.jar"
echo "Downloading from ${JAR_URL}..."
curl -sSL "${JAR_URL}" -o "${INSTALL_DIR}/companion.jar"

# Make executable
chmod +x "${INSTALL_DIR}/companion.jar"

# Add to PATH if not already
if [[ ":${PATH}:" != *":${INSTALL_DIR}:"* ]]; then
    SHELL_RC=""
    if [ -n "${ZSH_VERSION:-}" ]; then
        SHELL_RC="${HOME}/.zshrc"
    elif [ -n "${BASH_VERSION:-}" ]; then
        SHELL_RC="${HOME}/.bashrc"
    fi

    if [ -n "${SHELL_RC}" ]; then
        echo 'export PATH="${HOME}/.local/bin:${PATH}"' >> "${SHELL_RC}"
        echo "Added ${INSTALL_DIR} to PATH in ${SHELL_RC}"
        echo "Run 'source ${SHELL_RC}' or restart your shell"
    else
        echo "Add ${INSTALL_DIR} to your PATH manually"
    fi
fi

echo "Installation complete! Run 'companion --help' to get started."
echo "Or run: java -jar ${INSTALL_DIR}/companion.jar --help"