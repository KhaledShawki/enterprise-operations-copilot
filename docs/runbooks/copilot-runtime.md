# Copilot Runtime

This runbook enables and exercises the authenticated LLM Copilot on the local Docker Compose
platform.

The Copilot runtime is intentionally opt-in. The default local configuration keeps the LLM
orchestration disabled and configures no chat provider.

## Prerequisites

- the local platform prerequisites from [Local Platform](local-platform.md)
- a supported Spring AI chat provider account
- a provider API key
- a tenant, active tenant membership, and projected receivables data
- an OAuth access token issued by the local `eoc` realm for an authorized tenant user

The authenticated question endpoint requires one of these tenant roles:

- `tenant-admin`
- `operations-manager`
- `auditor`

## Configure the local runtime

Create the local environment file if it does not already exist:

```bash
cp deployment/compose/.env.example deployment/compose/.env
```

Keep `deployment/compose/.env` local. Do not commit provider credentials.

The checked-in defaults are fail-closed:

```text
EOC_COPILOT_LLM_ENABLED=false
EOC_COPILOT_LLM_PROVIDER=none
EOC_COPILOT_LLM_API_KEY=
EOC_COPILOT_LLM_MODEL=gpt-5-mini
```

To enable the OpenAI-backed local runtime, set these values only in
`deployment/compose/.env`:

```text
EOC_COPILOT_LLM_ENABLED=true
EOC_COPILOT_LLM_PROVIDER=openai
EOC_COPILOT_LLM_API_KEY=<local-provider-api-key>
EOC_COPILOT_LLM_MODEL=gpt-5-mini
```

The API key is passed to the local development container as an environment variable. This is
acceptable only for the local Compose workflow. Production deployment must use the target
platform's secret-management facility rather than a committed file or plain deployment manifest.

## Start or recreate the platform service

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  up -d --build --wait --wait-timeout 180 platform-service
```

Check readiness:

```bash
curl --fail --silent --show-error \
  http://localhost:8080/actuator/health/readiness
```

Expected status:

```json
{"status":"UP"}
```

## Obtain authentication

The platform service does not implement login. The local realm keeps the `eoc-web` client as a
public Authorization Code + PKCE client and the `platform-api` client as bearer-only.

Use the authentication flow described in [Local Keycloak](local-keycloak.md) or the frontend once
it is available, then export the resulting access token:

```bash
export ACCESS_TOKEN='<access-token>'
export TENANT_ID='<tenant-uuid>'
```

Do not put access tokens in committed scripts or documentation.

## Ask a grounded question

```bash
curl --fail-with-body --silent --show-error \
  -X POST \
  "http://localhost:8080/api/v1/tenants/${TENANT_ID}/copilot/questions" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Which receivable needs follow-up?",
    "businessDate": "2026-08-13"
  }'
```

A successful response contains deterministic answer text plus grounding metadata. It does not
expose prompts, provider messages, raw tool payloads, or audit identifiers.

The model may select only approved Copilot tools. Tenant identity comes from the authenticated
principal plus the authorized path tenant, and `businessDate` remains an application-owned typed
input.

## Verify durable audit records

Inspect only audit metadata; raw questions and answers are intentionally not persisted:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  exec platform-postgres \
  sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    SELECT execution_id,
           event_type,
           issuer,
           subject,
           tenant_id,
           business_date,
           failure_code,
           occurred_at
    FROM copilot_execution_audit_events
    ORDER BY occurred_at DESC
    LIMIT 10;
  "'
```

A successful execution should have one `STARTED` event followed by one `SUCCEEDED` event for the
same `execution_id`. A Copilot execution failure should have `STARTED` followed by `FAILED` with a
stable failure code.

Grounding and evidence are stored separately:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  exec platform-postgres \
  sh -ec 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    SELECT g.tool_call_id,
           g.tool_name,
           e.source_event_id,
           e.aggregate_version,
           e.occurred_at
    FROM copilot_execution_audit_groundings g
    LEFT JOIN copilot_execution_audit_evidence e
      ON e.audit_event_id = g.audit_event_id
     AND e.grounding_index = g.grounding_index
    ORDER BY g.audit_event_id, g.grounding_index, e.evidence_index;
  "'
```

## Diagnose common failures

| Symptom | Expected cause |
| --- | --- |
| `404` for the question endpoint | `EOC_COPILOT_LLM_ENABLED` is still `false` and the controller is not registered |
| `401 AUTHENTICATION_REQUIRED` | access token is missing or invalid |
| `403 ACCESS_DENIED` | authenticated user lacks an allowed role for the path tenant |
| `404 COPILOT_DATA_NOT_FOUND` | the selected deterministic receivable data does not exist |
| `502 COPILOT_UPSTREAM_PROTOCOL_ERROR` | the model returned a response outside the bounded orchestration contract |
| `503 COPILOT_UNAVAILABLE` | model, deterministic data source, or durable audit persistence is unavailable |

View the platform logs:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  logs --tail=200 platform-service
```

Public HTTP failures intentionally use stable generic Problem Details. Provider credentials and raw
provider exception details must not be exposed to callers.

## Disable the Copilot runtime

Set the local environment back to:

```text
EOC_COPILOT_LLM_ENABLED=false
EOC_COPILOT_LLM_PROVIDER=none
EOC_COPILOT_LLM_API_KEY=
```

Then recreate the service:

```bash
docker compose \
  --env-file deployment/compose/.env \
  -f deployment/compose/compose.yaml \
  up -d --force-recreate --wait --wait-timeout 180 platform-service
```

When disabled, the authenticated Copilot question controller is not registered.
