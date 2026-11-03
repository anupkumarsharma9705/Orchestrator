# High Availability (HA) Deployment

**HA is not supported or tested today.** This page previously described a
multi-node topology that the current server cannot provide:

- audit events are written to a local file, so replicas do not share them;
- build and scanner tools execute on the server host;
- there is no leader election or distributed job scheduler;
- the sandbox is local Docker, not a cluster-aware worker pool.

A single server with a managed PostgreSQL instance and persistent storage for
the audit log and artifacts is the supported deployment shape. See
[Architecture: Deployment](docs/architecture/deployment.md) for build, backup,
and rollback guidance.

If you need HA, design and test a queue/worker split first. Contributions that
move audit and job state into shared storage, add idempotency keys, and provide
a real worker pool are welcome.
