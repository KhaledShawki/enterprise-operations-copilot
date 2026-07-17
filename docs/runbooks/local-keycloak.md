# Local Keycloak

This runbook starts the local Keycloak identity provider and its PostgreSQL database.

The configuration uses Keycloak development mode and must not be used in production.

## Prerequisites

- Docker with Docker Compose
- Ports `8180` and `9000` available

Run all commands from the repository root.

## Configure the environment

Create the local environment file:

```bash
cp deployment/compose/.env.example deployment/compose/.env
```

Replace the example passwords in `deployment/compose/.env`.

The `.env` file contains local credentials and must not be committed.

## Start Keycloak

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  up -d --wait --wait-timeout 180
```

## Check container health

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  ps
```

Both `keycloak` and `keycloak-postgres` should be healthy.

## Verify the realm

```bash
curl --fail --silent --show-error \
  http://localhost:8180/realms/eoc/.well-known/openid-configuration \
  | python3 -c 'import json, sys; print(json.load(sys.stdin)["issuer"])'
```

Expected output:

```text
http://localhost:8180/realms/eoc
```

## Open the admin console

Open:

```text
http://localhost:8180/admin/
```

Sign in with `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` from the local `.env` file.

The imported `eoc` realm should contain:

- the `platform-api` bearer-only client
- the `eoc-web` public client
- the `platform-admin` realm role
- the local development user

## View logs

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --tail=150 keycloak
```

Follow logs continuously:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --follow keycloak
```

## Stop the services

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down
```

The PostgreSQL volume is preserved.

## Re-import the realm configuration

Keycloak does not overwrite an existing realm during startup import. After changing `eoc-realm.json`, remove the local database volume to force a clean import.

Warning: this permanently deletes all local Keycloak data.

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down --volumes
```

Start the services again using the normal start command.
