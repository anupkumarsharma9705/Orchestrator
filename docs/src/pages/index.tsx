import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';

export default function Home(): React.ReactElement {
  return (
    <Layout
      title="Java Companion Orchestrator"
      description="Generate secure Spring Boot REST APIs from natural language descriptions."
    >
      <header className="hero">
        <div className="container">
          <h1 className="heroTitle">Java Companion Orchestrator</h1>
          <p className="heroSubtitle">
            Generate Spring Boot REST APIs from natural language descriptions,
            with secure defaults, security scanning, testing, and auto-fixing.
          </p>
          <div className="heroButtons">
            <Link className="heroButton heroButtonPrimary" to="/docs/quick-start">
              Get Started →
            </Link>
            <Link className="heroButton heroButtonSecondary" to="/docs/installation">
              Installation
            </Link>
          </div>
        </div>
      </header>

      <main className="container margin-vert--lg">
        <div className="row">
          <div className="col col--4">
            <div className="card padding--lg">
              <h2>Offline skeleton</h2>
              <p>
                Generate a runnable Spring Boot skeleton locally with no server,
                Docker, or model-provider key. Add your domain code afterward.
              </p>
            </div>
          </div>
          <div className="col col--4">
            <div className="card padding--lg">
              <h2>Online pipeline</h2>
              <p>
                Run the full pipeline against the server: clarification,
                planning, code generation, verification, and auto-fixing.
              </p>
            </div>
          </div>
          <div className="col col--4">
            <div className="card padding--lg">
              <h2>Secure defaults</h2>
              <p>
                mTLS and API-key authentication on the server, stateless JWT in
                generated projects, and container-isolated verification.
              </p>
            </div>
          </div>
        </div>

        <div className="margin-top--lg">
          <p>
            <strong>Early access:</strong> build from source today. Homebrew,
            Scoop, Docker Hub, and prebuilt-JAR installs are not published yet.
            See <Link to="/docs/installation">Installation</Link> for the
            supported setup path.
          </p>
        </div>
      </main>
    </Layout>
  );
}
