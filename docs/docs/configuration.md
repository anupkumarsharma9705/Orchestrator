---
sidebar_position: 4
title: Configuration
---

# Configuration

Companion CLI stores configuration in `~/.companion/config.yaml`. All settings
can be managed with `companion config` commands.

## Config file location

| Platform | Path |
|----------|------|
| **macOS / Linux** | `~/.companion/config.yaml` |
| **Windows** | `%USERPROFILE%\.companion\config.yaml` |

## Configuration keys

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `serverUrl` | string | `https://localhost:8443` | Orchestrator server URL |
| `defaultProject` | string | `""` | Default project name for commands |
| `outputFormat` | string | `table` | `table`, `json`, `markdown`, or `plain` |
| `timeoutSeconds` | integer | `600` | Request timeout in seconds |
| `apiKey` | string | `""` | API key for server authentication |
| `logLevel` | string | `INFO` | `DEBUG`, `INFO`, `WARN`, or `ERROR` |
| `noColor` | boolean | `false` | Disable colored output |
| `ssl.enabled` | boolean | `true` | Use TLS for server communication |
| `ssl.trustStore` | string | `""` | Path to a PKCS12/JKS trust store (required for TLS) |
| `ssl.trustStorePassword` | string | `""` | Trust store password |
| `ssl.trustStoreType` | string | `PKCS12` | Trust store type |
| `ssl.verifyHostname` | boolean | `true` | Hostname verification; `false` is ignored |
| `ssl.mtlsEnabled` | boolean | `false` | Enable client-certificate authentication |
| `ssl.keyStore` | string | `""` | Client PKCS12/JKS key store (mTLS) |
| `ssl.keyStorePassword` | string | `""` | Client key store password |
| `ssl.keyStoreType` | string | `PKCS12` | Client key store type |
| `ssl.keyAlias` | string | `""` | Client certificate alias |

No trust store is bundled with the CLI. `scripts/dev-up` writes the development
CA path into `~/.companion/config.yaml`; production deployments must supply
their own trust store.

## Managing configuration

```bash
# List all settings
companion config list

# Read one value
companion config get serverUrl

# Set values
companion config set serverUrl https://api.example.com:8443
companion config set defaultProject my-project
companion config set outputFormat json
companion config set timeoutSeconds 300
companion config set apiKey your-secret-api-key
companion config set logLevel DEBUG
companion config set noColor true

# Show the config file path
companion config path
```

## Output formats

| Format | Description |
|--------|-------------|
| `table` | Human-readable table (default) |
| `json` | JSON for scripting |
| `markdown` | Markdown tables for docs |
| `plain` | Key=value pairs |

```bash
companion config list --output-format json
companion task list --output-format markdown
```

## Environment variables

Environment variables override the config file. Only these variables are read:

| Env var | Config key |
|---------|------------|
| `COMPANION_SERVER_URL` | `serverUrl` |
| `COMPANION_API_KEY` | `apiKey` |
| `COMPANION_SSL_TRUSTSTORE` | `ssl.trustStore` |
| `COMPANION_SSL_TRUSTSTORE_PASSWORD` | `ssl.trustStorePassword` |
| `COMPANION_SSL_KEYSTORE` | `ssl.keyStore` |
| `COMPANION_SSL_KEYSTORE_PASSWORD` | `ssl.keyStorePassword` |
| `COMPANION_SSL_MTLS_ENABLED` | `ssl.mtlsEnabled` |

Passwords supplied through the environment never need to be written to disk:

```bash
export COMPANION_API_KEY=your-secret-key
export COMPANION_SSL_TRUSTSTORE_PASSWORD=changeit
companion generate "..." --name test
```

## TLS configuration

The CLI always validates the server certificate and hostname. There is no
supported option to skip certificate validation.

### Production (CA-signed certificate)

```yaml
serverUrl: "https://orchestrator.example.com:8443"
ssl:
  enabled: true
  trustStore: "/etc/companion/truststore.p12"
  trustStorePassword: "use-an-env-var-instead"
  trustStoreType: PKCS12
  verifyHostname: true
```

### Development (local CA from `scripts/dev-up`)

`scripts/dev-up` generates a local CA plus separate server and client
certificates. It writes the trust store path and mTLS settings into
`~/.companion/config.yaml`; no manual TLS configuration is required.

### mTLS

```yaml
ssl:
  enabled: true
  trustStore: "/etc/companion/truststore.p12"
  trustStorePassword: "use-an-env-var-instead"
  verifyHostname: true
  mtlsEnabled: true
  keyStore: "/etc/companion/client-keystore.p12"
  keyStorePassword: "use-an-env-var-instead"
  keyStoreType: PKCS12
  keyAlias: client
```

`ssl.verifyHostname: false` is accepted by the parser but ignored with a
warning; hostname verification always remains enabled.

## Configuration validation

```bash
companion doctor
```

Checks Java and Maven versions, Docker availability, Git, server connectivity,
and TLS configuration.

## Example config file

```yaml
# ~/.companion/config.yaml
serverUrl: "https://localhost:8443"
defaultProject: "my-team-project"
outputFormat: "table"
timeoutSeconds: 600
apiKey: ""
logLevel: "INFO"
noColor: false
ssl:
  enabled: true
  trustStore: "/home/me/.companion/certs/truststore.p12"
  trustStorePassword: ""
  trustStoreType: "PKCS12"
  verifyHostname: true
  mtlsEnabled: true
  keyStore: "/home/me/.companion/certs/client-keystore.p12"
  keyStorePassword: ""
  keyStoreType: "PKCS12"
  keyAlias: "client"
```

## Security notes

- **Never commit** `config.yaml` to version control.
- API keys are **masked** in `config list` output.
- Prefer environment variables for CI/CD secrets.
- Use `https://` for every non-local server. The CLI does not offer an
  insecure TLS mode.

## Next steps

- [Quick Start](/docs/quick-start) — Generate your first project
- [Offline Mode](/docs/offline-mode) — Generate without server
- [CLI Reference](/docs/cli-reference) — All commands and flags
