# Local Platform

This runbook operates the complete local Enterprise Operations Copilot platform:

- `platform-service`
- `platform-postgres`
- `keycloak`
- `keycloak-postgres`
- `kafka`
- one-shot `kafka-topic-init`

The configuration is intended only for local development. It is not a production
deployment model.

## Prerequisites

- Docker with Docker Compose
- `curl`
- Python 3 for formatting JSON responses
- ports `8080`, `8180`, and `9000` available on the loopback interface

Run all commands from the repository root.

## Configure the environment

Create the local environment file if it does not already exist:

```bash
cp deployment/compose/.env.example deployment/compose/.env
```

Replace every example password in `deployment/compose/.env`. The file contains local
credentials, is ignored by Git, and must not be committed.

Changing database credentials in `.env` does not update an existing PostgreSQL data
volume. Use the reset procedure only when a clean local environment is intended.

## Validate the configuration

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  config --quiet
```

The command should exit successfully without output.

## Start the platform

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  up --detach --build --wait --wait-timeout 240
```

Compose waits for both PostgreSQL databases, Keycloak, Kafka, the Connector integration-events
topic initialization, and the platform service. The one-shot `kafka-topic-init` container must exit
successfully before the platform service starts.

## Check service health

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  ps --all
```

The five long-running services should be running, health-checked services should report `healthy`,
and `kafka-topic-init` should report a successful exit.

Verify the application probes:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:8080/actuator/health/liveness \
  | python3 -m json.tool

curl --fail --silent --show-error \
  http://127.0.0.1:8080/actuator/health/readiness \
  | python3 -m json.tool
```

Both responses should report:

```json
{
  "status": "UP"
}
```

Verify that the Connector integration-events topic exists with the expected local partition count:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:9092 \
  --describe \
  --topic "$(grep '^EOC_CONNECTOR_EVENTS_KAFKA_TOPIC=' deployment/compose/.env | cut -d= -f2-)"
```

The topic should report six partitions and replication factor one in the single-node local
environment. Topic creation is handled by Compose only for local development; production topics are
provisioned by infrastructure automation.

Verify that business APIs remain protected:

```bash
curl --silent --show-error \
  --output /dev/null \
  --write-out '%{http_code}\n' \
  http://127.0.0.1:8080/api/v1/me
```

Expected output:

```text
401
```

## Verify database migrations

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  exec -T platform-postgres sh -ec \
  'psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --tuples-only --no-align --command "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank;"'
```

Expected output:

```text
1
2
3
4
5
6
7
8
9
10
11
12
```

Flyway owns schema migration. Hibernate validates the migrated schema and must not
create or update it.

## Verify database isolation

The platform database is reachable by containers on the private `platform` network
and is not published to the host.

```bash
docker inspect \
  --format 'PlatformDatabasePortBindings={{json .HostConfig.PortBindings}}' \
  eoc-platform-postgres-1
```

Expected output:

```text
PlatformDatabasePortBindings={}
```

## Verify Keycloak

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:8180/realms/eoc/.well-known/openid-configuration \
  | python3 -c 'import json, sys; print(json.load(sys.stdin)["issuer"])'
```

Expected output:

```text
http://localhost:8180/realms/eoc
```

The `eoc-web` client uses Authorization Code with PKCE. Direct access grants are
intentionally disabled. Do not enable password grants to simplify local API calls.
The application authentication and authorization contract is covered by the Spring
integration tests until the browser client provides the interactive PKCE flow.

## View logs

Display recent platform logs:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --tail=150 platform-service platform-postgres kafka
```

Display recent identity-provider logs:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --tail=150 keycloak keycloak-postgres
```

Follow the platform service continuously:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --follow platform-service
```

## Verify restart persistence

Stop and remove the containers without deleting their named volumes:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down
```

Start the platform again:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  up --detach --build --wait --wait-timeout 240
```

Run the service-health and migration checks again. The services should become healthy,
and the Flyway history should still contain versions `1` through `12`.

## Stop the platform

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down
```

The named PostgreSQL and Kafka volumes are preserved.

## Reset all local data

Warning: this permanently deletes both local PostgreSQL databases and the local Kafka log. Use it
only when an intentionally clean environment is required.

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down --volumes --remove-orphans
```

The next startup recreates both databases and the Kafka data volume, imports the Keycloak realm,
runs Flyway migrations, recreates the local Connector integration-events topic, and validates the
application schema.

## Diagnose an unhealthy platform service

Inspect the latest healthcheck attempts:

```bash
docker inspect \
  --format '{{range .State.Health.Log}}{{println "ExitCode=" .ExitCode}}{{println .Output}}{{end}}' \
  eoc-platform-service-1
```

Then inspect the application logs:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --tail=200 platform-service
```

Common causes include an unavailable database, invalid local credentials, occupied host ports, a
Kafka topic-initialization failure, or a Keycloak realm that was not re-imported after its source
configuration changed. A temporary Kafka outage after startup does not make application readiness
depend on the broker; Connector events remain durable in the PostgreSQL outbox and are retried.
