# ADR 0010: Add an authenticated Copilot question HTTP API

## Status

Accepted

## Context

The Copilot bounded context now exposes deterministic tools, stateless MCP transport, provider-neutral
LLM orchestration, and durable execution auditing. The orchestration is not yet reachable through the
product's normal authenticated HTTP surface.

The HTTP boundary must not weaken the trust boundaries established by the earlier Copilot decisions.
In particular, tenant identity must not come from the request body or the language model, business
date must remain an application-owned typed input, and HTTP callers must receive grounded business
facts rather than provider-specific model metadata.

## Decision

Expose a tenant-scoped endpoint:

`POST /api/v1/tenants/{tenantId}/copilot/questions`

The request body contains only:

- `question`
- optional `businessDate`

The tenant is selected by the path and authorized through the existing DB-backed
`tenantAccessPolicy`. The authenticated JWT is mapped through the existing
`JwtAuthenticatedUserMapper`, and the web adapter creates the trusted `CopilotExecutionContext`.

The endpoint delegates only to `AskCopilotUseCase`. It does not call Spring AI, MCP, Analytics,
Operations, Connector Management, Audit persistence, or a model provider directly.

The response contains the deterministic answer text plus grounding references:

- tool call id
- approved tool contract name
- source event id/version/timestamp where the selected deterministic tool exposes source evidence

Audit identifiers, provider metadata, prompts, model messages, and raw tool payloads are not exposed.

The endpoint is conditional on `eoc.copilot.llm.enabled=true`, matching the orchestration runtime.
When LLM orchestration is disabled, the HTTP endpoint is not registered.

The existing centralized Problem Detail contract is extended for Copilot failures:

- authorization failure -> 403
- deterministic data not found -> 404
- model/tool/audit unavailable -> 503
- model protocol, grounding, orchestration-limit, or model-generated invalid tool arguments -> 502
- corrupted deterministic data -> 500

Public failures use stable codes and generic details. Provider, persistence, and internal exception
messages are not returned to clients.

## Consequences

- The product gains a normal authenticated HTTP entry point without making the model a trust
  boundary.
- Tenant authorization remains authoritative in Tenant Access and is also enforced again by
  deterministic Copilot execution.
- Business-date replay remains explicit and testable.
- API consumers receive evidence-backed results without coupling to Spring AI or MCP.
- Disabling LLM orchestration removes this endpoint from the application context.

## Not in scope

- conversation persistence or chat history
- streaming responses
- provider-specific request/response fields
- arbitrary model-selected tenant or business date
- exposing audit records through this endpoint
- RAG, vector search, embeddings, or memory
