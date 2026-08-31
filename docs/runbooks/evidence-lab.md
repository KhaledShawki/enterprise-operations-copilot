# Evidence Lab

The Evidence Lab produces reproducible, machine-readable system evidence from outside the EOC
application boundary.

## Prerequisites

- the repository's pinned Python 3.12 runtime
- Git
- Docker with Docker Compose
- a configured `deployment/compose/.env`
- the local platform defined in `deployment/compose/compose.yaml`

The lab client secret is required in the local environment file:

```text
EOC_LAB_CLIENT_SECRET=<replace-the-example-value>
```

The lab refuses the committed `change-me-*` example value. The secret is consumed only by Keycloak
and the local lab process. It must not be added to platform service configuration, committed
evidence, logs, or command-line arguments.

If the Keycloak realm was already imported before the lab client existed, reset the local Keycloak
data volume so the updated realm is imported. The complete reset procedure is documented in the
Local Platform runbook.

## Start the local platform

From the repository root, start the two long-running application services. Docker Compose starts
their PostgreSQL, Keycloak, Kafka, and topic-initialization dependencies automatically:

```bash
docker compose \
  --env-file deployment/compose/.env \
  --file deployment/compose/compose.yaml \
  up \
  --detach \
  --build \
  --wait \
  --wait-timeout 240 \
  platform-service web-bff
```

After startup, `kafka-topic-init` is expected to show `Exited (0)`. It is a one-shot initializer, not
a long-running service. Kafka, Keycloak, the databases, `platform-service`, and `web-bff` should be
running and healthy.

Check the complete state with:

```bash
docker compose \
  --env-file deployment/compose/.env \
  --file deployment/compose/compose.yaml \
  ps --all
```

## Validate the lab environment

From the repository root:

```bash
./scripts/eoc-lab doctor
```

The doctor is read-only. It verifies:

- Docker Engine availability
- Compose configuration
- required running services
- platform liveness and readiness
- exact OIDC issuer discovery
- anonymous rejection of the protected API
- service-account authentication through the platform
- the four Kafka topics with six partitions each

The network address used to reach Keycloak and the logical OIDC issuer are separate settings. The
local defaults are:

```text
Keycloak network URL: http://127.0.0.1:8180
Expected issuer:      http://localhost:8180/realms/eoc
```

For a non-default topology, override them independently:

```bash
./scripts/eoc-lab \
  --keycloak-url http://127.0.0.1:18180 \
  --expected-issuer http://localhost:18180/realms/eoc \
  doctor
```

A failed check returns a non-zero process exit code and does not hide later checks; the complete set
is printed to aid diagnosis.

## Run the smoke scenario

```bash
./scripts/eoc-lab run smoke
```

Before changing business state, the lab fingerprints the Git source tree, runs the doctor checks, and
captures runtime provenance. If that preflight fails, the scenario still records an `ERROR` evidence
artifact instead of losing the failed run.

A successful execution:

1. obtains a short-lived client-credentials access token for `eoc-lab`
2. verifies that the authenticated API identity has the expected issuer and `platform-admin`
3. creates a uniquely keyed tenant through `POST /api/v1/tenants`
4. verifies HTTP `201`, JSON media type, UUID identity, and the `Location` header
5. follows the returned `Location` through `GET`
6. verifies HTTP `200` and the stable public tenant fields
7. finalizes the run atomically

Additional response fields are permitted because additive fields are compatible within `/v1`.

## Run the deterministic baseline

The baseline uses an external gRPC Mock ERP from the evidence-only Compose overlay. Set a separate
workload client secret in `deployment/compose/.env`, reset Keycloak volumes if the realm predates the
workload client, and start:

```bash
docker compose \
  --env-file deployment/compose/.env \
  --file deployment/compose/compose.yaml \
  --file deployment/compose/compose.evidence.yaml \
  up \
  --detach \
  --build \
  --wait \
  --wait-timeout 240 \
  platform-service web-bff mock-erp
```

Then execute:

```bash
./scripts/eoc-lab run baseline --records 137 --seed 42
```

The control-plane `eoc-lab` identity creates the tenant and membership. A distinct
`eoc-lab-workload` service account has no platform-wide realm role; after self-provisioning it is
assigned the tenant-scoped `tenant-admin` role and performs connector creation/imports/reads.

The Mock ERP control service is bound only to loopback inside its own container. The lab configures
it through `docker compose exec`; `platform-service` can reach only the public source gRPC service.
The dataset fingerprint is checked against an independent Python implementation before tenant
business mutation begins.

The baseline imports customers before invoices, then compares deterministic expected business facts
with Operations, Operations canonical projection facts with Analytics, event lineage invariants, and
the Analytics receivables summary. Analytics non-convergence is recorded as `FAIL`, not `ERROR`.

## Terminal states

Every started scenario records one terminal state:

- `PASS` — execution completed and all scenario invariants are true
- `FAIL` — execution completed but at least one system invariant is false
- `ERROR` — infrastructure, authentication, protocol, or execution prevented a valid comparison

`result.json` contains a stable `phase` and, for `ERROR`, an error `code`. The lab does not persist
access tokens, client secrets, or Python stack traces.

## Evidence artifacts

Each completed run appears only after all files are written:

```text
evidence/runs/<run-id>/
├── manifest.json
├── result.json
└── checksums.sha256
```

Hidden `.partial-*` directories are implementation details and are removed when an ordinary write
fails. A process crash may leave a hidden partial directory, but it is never interpreted as a
completed run.

`manifest.json` records the context needed to understand and reproduce the run, including:

- commit, branch, and dirty state
- tracked-diff fingerprint
- untracked paths and aggregate content fingerprint
- timestamps and scenario version
- Python/host metadata
- Docker Engine and Compose versions
- running service image references and image ids
- source Compose-file hash and non-secret runtime-configuration fingerprint
- platform URL, Keycloak network URL, expected issuer, client id, and Kafka topic names

`result.json` records doctor checks, terminal state, metrics, invariants, and non-secret resource
identity.

`checksums.sha256` provides content-integrity hashes for the two JSON files. It is not a digital
signature; anyone able to modify all evidence files can recompute the hashes.

Generated runs are ignored by Git. Later PRs may commit selected reference results only after their
methodology and hardware documentation are stable.

## Verify the lab tests

```bash
PYTHONPATH=tools/eoc-lab python3 -m unittest discover -s tools/eoc-lab/tests -v
```

The harness uses only the Python standard library.

## CI behavior

The normal CI contains both fast unit/contract tests and a real system smoke job. The system job:

```text
fresh Compose volumes
      ↓
Keycloak realm import
      ↓
PostgreSQL + Kafka + platform + BFF
      ↓
eoc-lab doctor
      ↓
eoc-lab run smoke
      ↓
verify PASS
      ↓
upload evidence artifact
      ↓
teardown volumes
```

The evidence upload runs even when the smoke step fails, so an `ERROR` or `FAIL` result remains
available for diagnosis when the harness was able to finalize it.
