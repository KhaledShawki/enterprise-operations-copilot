# Local Keycloak

This runbook starts the local Keycloak identity provider and its PostgreSQL database.

The configuration uses Keycloak development mode and must not be used in production.

To start Keycloak together with the platform service and its database, use the
[Local Platform runbook](local-platform.md).

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
  up -d --wait --wait-timeout 180 keycloak
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

## Configure the platform resource server

The platform service validates JWT access tokens issued by the local `eoc` realm.

The default local configuration is defined in `services/platform-service/src/main/resources/application.properties`.

| Environment variable | Local default | Purpose |
| --- | --- | --- |
| `EOC_SECURITY_ISSUER_URI` | `http://localhost:8180/realms/eoc` | Identifies the trusted token issuer |
| `EOC_SECURITY_JWK_SET_URI` | `http://localhost:8180/realms/eoc/protocol/openid-connect/certs` | Provides the public keys used to verify JWT signatures when the platform runs on the host |
| `EOC_SECURITY_AUDIENCE` | `platform-api` | Identifies the required access-token audience |

The full Docker Compose stack overrides the JWK Set URI with the internal address
`http://keycloak:8080/realms/eoc/protocol/openid-connect/certs`. The issuer remains
`http://localhost:8180/realms/eoc` because it must match the token's `iss` claim.
These values are intended only for local development. Override them when running
against another Keycloak environment.

The platform service:

- validates JWT signatures, expiration, issuer, and audience
- maps OAuth scopes to authorities prefixed with `SCOPE_`
- maps Keycloak roles from `realm_access.roles` to authorities prefixed with `ROLE_`
- operates without HTTP sessions
- requires authentication for every endpoint unless explicitly configured otherwise

Creating a tenant requires the `platform-admin` realm role:

```http
POST /api/v1/tenants
Authorization: Bearer <access-token>
Content-Type: application/json
```

Authentication and authorization failures use RFC 9457 Problem Details responses:

| Situation | HTTP status | Problem code |
| --- | --- | --- |
| Access token is missing or invalid | `401 Unauthorized` | `AUTHENTICATION_REQUIRED` |
| Token is valid but lacks `platform-admin` | `403 Forbidden` | `ACCESS_DENIED` |

The platform service does not implement login or password management. Keycloak remains responsible for authentication and credential handling.

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

Warning: the following commands permanently delete all local Keycloak data. They
preserve the platform PostgreSQL volume.

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  down

docker volume rm eoc_keycloak-postgres-data
```

Start Keycloak again using the normal identity-provider startup command.
