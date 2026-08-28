# ADR 0011: Establish an external evidence and reliability lab

## Status

Accepted

## Context

The platform already implements tenant isolation, transactional outbox publication, Kafka delivery,
idempotent inbox consumption, Analytics projections, dead-letter recovery, deterministic Copilot
tools, and a full-stack receivables slice. Unit and integration tests verify individual contracts and
failure windows, but they do not provide a reproducible system-level evidence trail that an external
reviewer can inspect and rerun.

The next project phase must measure observable system behavior without weakening the production
architecture or coupling measurements to implementation details. A harness that calls Java services
directly, reads JPA repositories, or queries internal tables would measure a shortcut rather than the
deployed system boundary.

Because later reliability and performance claims will rely on these runs, evidence needs stronger
integrity guarantees than ordinary development tooling. Failed scenarios must remain visible, a
partially written directory must never look complete, and every run must identify the source and
runtime that produced it.

## Decision

Introduce `tools/eoc-lab` as an external black-box harness.

The lab is not part of the Maven reactor and does not import application, domain, persistence, or
Spring implementation modules. It may interact only through documented operational boundaries:

- public HTTP APIs
- the local OIDC token endpoint
- Kafka when a scenario explicitly exercises transport behavior
- Docker Compose for local environment inspection and later controlled fault injection
- published telemetry once observability is introduced

System-state reconciliation will prefer public authoritative and projection APIs. Direct database
access is not part of the normal evidence path and, if ever required for an explicit forensic
experiment, must be isolated and documented as such.

Every scenario that starts produces a machine-readable `PASS`, `FAIL`, or `ERROR` result. `FAIL`
means execution completed but a system invariant was false. `ERROR` means infrastructure, protocol,
authentication, or harness execution prevented a valid comparison. Error evidence includes a stable
phase and code, but never credentials or stack traces.

Runs are published atomically. The lab first writes the manifest, result, and checksum files into a
hidden partial directory. Only after every artifact is complete and flushed to disk is that directory
renamed to the final run id. Existing run ids are never overwritten.

The run manifest records:

- Git commit and branch
- dirty-worktree state including untracked files
- fingerprints for tracked changes and untracked file contents
- host/Python metadata
- Docker Engine and Compose versions
- running service image references and immutable image ids
- a non-secret configuration fingerprint
- network Keycloak address separately from the expected OIDC issuer identity

The local lab authenticates with a dedicated confidential Keycloak client using the OAuth 2.0
client-credentials grant. Its service account receives only the existing `platform-admin` realm role
needed for platform-level lab setup. The client secret remains in the ignored local Compose `.env`
file. Direct password grants remain disabled, and the browser/BFF authentication model is unchanged.
Future business workload scenarios must use normal tenant-scoped actors; the lab service account is a
control-plane identity, not a substitute for tenant authorization tests.

The initial smoke scenario deliberately stays small. Its purpose is to prove that the lab can observe
the real local system, authenticate through the configured identity flow, exercise one protected
write/read path, and produce trustworthy evidence. It validates infrastructure, Kafka topic topology,
REST status codes, JSON media type, `Location` identity, UUID resource identity, and the stable public
tenant fields while permitting additive v1 response fields.

CI has two levels:

1. fast standard-library unit/contract tests for the harness and Keycloak configuration
2. a real Docker Compose smoke job that starts fresh Keycloak/PostgreSQL/Kafka/platform/BFF services,
   runs `doctor`, executes the scenario, verifies a `PASS` artifact, uploads the evidence, and tears
   the environment down

Deterministic business datasets, reconciliation, metrics, duplicate-delivery campaigns, crash
recovery, broker outages, DLT campaigns, and load testing remain separate incremental changes built
on this contract.

## Consequences

- Evidence is produced from the same external boundaries used by real clients and operators.
- Production domain and persistence code remain unaware of portfolio/benchmark tooling.
- Failure evidence remains available instead of being lost when a scenario aborts.
- If a run directory is visible under `evidence/runs`, its evidence bundle has been fully finalized.
- Results can be interpreted against source-tree and container-image provenance.
- CI verifies the actual identity and Compose wiring rather than only testing Python helpers.
- Each later reliability or performance change can produce a runnable result immediately.
- Local lab credentials add one development-only secret that must be changed from the example value.
- The initial smoke run creates one local tenant per execution; local evidence environments should be
  reset periodically using the existing Compose reset procedure.

## Rejected alternatives

### Put the evidence runner inside `platform-service`

Rejected because it would make measurements depend on internal application composition and create a
production-facing maintenance/security surface for test orchestration.

### Query PostgreSQL directly for all assertions

Rejected because it bypasses authorization, API contracts, transaction boundaries, and future
service extraction. Reconciliation should observe system-owned interfaces first.

### Reuse the interactive development user or enable password grants

Rejected because the browser client intentionally uses Authorization Code with PKCE and disables
direct password grants. Weakening authentication solely for automation would regress the existing
security model.

### Write evidence only after successful execution

Rejected because the most valuable reliability evidence often comes from failures. Started scenarios
must preserve terminal state even when they end in `ERROR`.

### Publish only Git commit and ignore local changes

Rejected because a commit id alone does not identify the source tree when tracked or untracked code
has changed.

### Add Prometheus, tracing, load generation, and fault injection in this first change

Rejected because the evidence contract itself should be executable and trustworthy before additional
measurement infrastructure is introduced.
