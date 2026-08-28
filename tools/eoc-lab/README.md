# EOC Evidence Lab

`eoc-lab` is an external black-box harness for producing reproducible evidence about the running
Enterprise Operations Copilot system. It is intentionally not a Maven module and does not import
application, domain, persistence, or Spring implementation code.

The first smoke scenario verifies that the lab can observe the running system, authenticate through
the real local identity flow, exercise a protected API round trip, and produce trustworthy evidence:

- local Compose configuration and required services are available
- platform liveness/readiness are `UP`
- the Keycloak issuer identity is validated independently of its network address
- the protected API rejects anonymous access
- a dedicated client-credentials identity is accepted as `platform-admin`
- the four local Kafka topics exist with six partitions
- one tenant can be created and read back through the public REST API
- the create `Location` and stable public representation are validated
- `PASS`, `FAIL`, and `ERROR` runs are persisted
- source/runtime provenance is recorded without credentials
- run directories are finalized atomically with SHA-256 content-integrity files

## Run

Copy and customize the local environment file, then start the two long-running application services.
Compose starts their PostgreSQL, Keycloak, Kafka, and topic-initialization dependencies automatically:

```bash
cp deployment/compose/.env.example deployment/compose/.env
# replace all example secrets, including EOC_LAB_CLIENT_SECRET

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

`kafka-topic-init` is expected to exit with code `0` after creating the required topics. Validate the
running environment without mutating business data:

```bash
./scripts/eoc-lab doctor
```

Execute the smoke scenario:

```bash
./scripts/eoc-lab run smoke
```

Generated artifacts are written under `evidence/runs/<run-id>/` and are intentionally ignored by
Git. Curated reference runs will be added separately when the measurement methodology is stable.

## Test

The lab uses only the Python standard library:

```bash
PYTHONPATH=tools/eoc-lab python3 -m unittest discover -s tools/eoc-lab/tests -v
```

The repository pins Python through `.python-version`; the lab requires Python 3.12 or newer.

## Trust boundary

The lab may call public HTTP APIs, the local Keycloak token endpoint, Docker Compose, and Kafka's
administrative CLI inside the local broker container. It must not:

- import EOC Java modules
- connect directly to PostgreSQL for scenario assertions
- execute arbitrary SQL
- bypass application authorization
- log or persist access tokens or client secrets
- add lab-only behavior to normal production profiles

The `eoc-lab` service account is a control-plane identity for lab setup. Later business workload and
tenant-isolation scenarios must use ordinary tenant-scoped actors.

Later reliability scenarios may use explicit lab-only fault-control adapters, but those adapters must
remain disabled outside the dedicated lab profile.
