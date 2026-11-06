---
sidebar_position: 1
slug: /
---

# Java Companion Orchestrator

Generate Spring Boot REST APIs from natural language descriptions, with secure defaults, security scanning, testing, and auto-fixing.

> **Early access:** build from source today. Homebrew, Scoop, Docker Hub, and
> prebuilt-JAR installs are not published yet.

<div className="heroButtons">
  <a className="heroButton heroButtonPrimary" href="/docs/quick-start">
    Get Started →
  </a>
  <a className="heroButton heroButtonSecondary" href="/docs/installation">
    Installation
  </a>
</div>

## Features

<div className="featuresGrid">
  <div className="featureCard">
    <span className="featureIcon">🚀</span>
    <h3 className="featureTitle">Natural Language to Code</h3>
    <p className="featureDescription">Online mode turns a plain-English description into a plan and domain code. Offline mode deterministically generates a secure Spring Boot skeleton with no LLM.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">🛡️</span>
    <h3 className="featureTitle">Built-in Security</h3>
    <p className="featureDescription">Secret scanning, SpotBugs/FindSecBugs static analysis, and an optional OWASP dependency-check profile in generated projects.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">🔄</span>
    <h3 className="featureTitle">Auto-Fix Loop</h3>
    <p className="featureDescription">In online mode, failed builds trigger an auto-fix loop: analyze errors, generate fixes, rebuild, and verify for up to 5 iterations.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">🏗️</span>
    <h3 className="featureTitle">Isolated Sandbox</h3>
    <p className="featureDescription">Every build runs in a hardened Docker container: dropped capabilities, read-only rootfs, no network, resource limits, Maven cache isolation.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">🤖</span>
    <h3 className="featureTitle">Multi-LLM Support</h3>
    <p className="featureDescription">OpenRouter (enabled by default), NVIDIA, and local Ollama providers, with circuit breakers, cost tracking, and budgets.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">📦</span>
    <h3 className="featureTitle">Offline Mode</h3>
    <p className="featureDescription">Generate a secure skeleton without any server: `companion generate "..." --name demo --offline`. Suited to air-gapped environments and quick prototyping.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">🔐</span>
    <h3 className="featureTitle">Human-in-the-Loop</h3>
    <p className="featureDescription">Interactive clarification questions, plan review with approval/reject, non-interactive mode for CI/CD pipelines.</p>
  </div>

  <div className="featureCard">
    <span className="featureIcon">📊</span>
    <h3 className="featureTitle">Observability</h3>
    <p className="featureDescription">Structured audit logs, health endpoints, and cost tracking with budgets and alerts.</p>
  </div>
</div>

## Quick Example

```bash
# Build the CLI from source
mvn -B -ntp -pl app-cli -am package -DskipTests

# Generate a secure Spring Boot skeleton (no server or LLM needed)
java -jar app-cli/target/app-cli-0.0.1-SNAPSHOT-exec.jar generate \
  "REST CRUD for Book entity with author, genre, ISBN, pagination, validation" \
  --name library --offline
```

Online generation (server stack plus a model-provider key) can turn a plan into
domain code. It is early access and requires the server workflow described in
the repository README.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        companion.jar                          │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────┐  │
│  │   CLI       │  │   ENGINE     │  │     TOOLS          │  │
│  │  (picocli)  │  │  (Library)   │  │  (20+)             │  │
│  └──────┬──────┘  └──────┬───────┘  └────────┬───────────┘  │
│         │                │                   │               │
│    ┌────┴────┐    ┌──────┴──────┐    ┌───────┴───────────┐     │
│    │ Clarifier│    │ ModelRouter │    │ File/Build/       │     │
│    │ Refiner  │    │ (Providers) │    │ Scan/Fix Tools    │     │
│    │ Planner  │    │ CostTracker │    │ Verifier          │     │
│    │ Coder    │    │ CircuitBrkr │    │ Sandbox           │     │
│    │ Fixer    │    │             │    │                   │     │
│    └─────────┘    └─────────────┘    └───────────────────┘     │
└─────────────────────────────────────────────────────────────┘
                                │
                      ┌─────────┴─────────┐
                      ▼                   ▼
              ┌───────────────┐   ┌───────────────┐
              │ PostgreSQL    │   │  Sandbox      │
              │  (Jobs, Rounds,│   │  (Docker)     │
              │   Findings)   │   │  Build/Test/  │
              │               │   │  Scan/Fix     │
              └───────────────┘   └───────────────┘
```

## Next Steps

<div className="callout calloutInfo">
  <strong>New to Companion?</strong> Start with the <a href="/docs/quick-start">Quick Start</a> guide to generate your first project in 5 minutes.
</div>

<div className="callout calloutInfo">
  <strong>Building from source?</strong> See <a href="/docs/installation">Installation</a> for the supported setup path.
</div>

<div className="callout calloutInfo">
  <strong>Working offline?</strong> Check out <a href="/docs/offline-mode">Offline Mode</a> for air-gapped environments.
</div>

## Links

- **Source code, issues, and releases:** use the repository that hosts your clone.
- **API schema:** the running server exposes OpenAPI at `/api-docs` and Swagger UI
  at `/swagger-ui.html`.