# ADR 0009: Add durable Copilot execution audit

## Status

Accepted.

## Context

The Copilot now has deterministic tools, stateless MCP exposure, and provider-neutral LLM orchestration. The next product-facing step is an authenticated question API. Exposing that path before durable execution attribution would create a production capability without a reliable audit trail.

Copilot execution crosses a probabilistic model boundary and can read tenant-scoped financial facts. The platform therefore needs to preserve who initiated an execution, which tenant and business date were used, whether the execution completed, and which deterministic evidence grounded a successful answer.

Raw user questions, model messages, tool-result payloads, and final answers may contain sensitive business data. Durable auditability does not require retaining those payloads.

## Decision

Introduce a framework-neutral `audit` bounded context before the user-facing Copilot HTTP API.

Each Copilot execution receives one application-generated `executionId`. The Platform composition boundary records an append-only sequence:

1. `STARTED` before invoking the Copilot orchestration;
2. exactly one terminal `SUCCEEDED` or `FAILED` event.

`STARTED` is written first so a process crash or interrupted model call leaves a durable incomplete execution rather than no trace.

The Audit context owns its event model, input use case, append output port, validation, timestamps, and audit-event identities. It does not depend on Copilot or any framework. Platform maps trusted Copilot context and grounding into Audit-owned contracts.

The durable record contains:

- issuer, subject, and tenant identity from the trusted Copilot execution context;
- the application-selected optional business date;
- SHA-256 digest and length of the normalized question;
- execution and audit-event identifiers and timestamps;
- on success, SHA-256 digest and length of the deterministic answer plus tool-call grounding and source-event evidence;
- on failure, a stable Audit-owned failure category.

The audit record does **not** persist raw questions, answers, model prompts/messages, tool-result payloads, credentials, or provider-specific request/response objects.

Platform persists the Audit contracts through an explicit JDBC adapter and Flyway-managed PostgreSQL tables. The schema enforces one start event and at most one terminal event per execution.

Audit is fail-closed for Copilot execution:

- if `STARTED` cannot be durably appended, the Copilot delegate is not invoked;
- if the terminal audit event cannot be appended, the request fails with a stable audit-unavailable error;
- when Copilot fails and failure-audit persistence also fails, audit unavailability takes precedence and the original Copilot failure is retained as suppressed context.

## Consequences

Copilot core remains provider-, transport-, persistence-, and Audit-independent. Audit becomes independently extractable later as `audit-service` without changing Copilot application contracts.

The database stores enough immutable metadata to attribute executions and trace successful business facts back to deterministic source events while minimizing retained sensitive content.

A user-facing Copilot HTTP endpoint, conversation persistence, audit query APIs, provider telemetry, and retention/archival policy are deliberately outside this decision. The HTTP question API can follow as the next slice and depend on the audited `AskCopilotUseCase` bean.

## Alternatives considered

### Expose the HTTP Copilot API first and add audit later

Rejected because production-facing LLM executions would temporarily exist without durable attribution.

### Put audit contracts inside the Copilot module

Rejected because audit is a cross-cutting bounded context with an explicit future service boundary. Copilot should not own the durable history model.

### Persist raw prompts and answers

Rejected because it unnecessarily increases sensitive-data retention. Digests, lengths, trusted identity, failure categories, and deterministic grounding provide the required execution trace for this foundation.

### Use JPA entities for audit persistence

Not selected for this append-only foundation. Plain JDBC keeps the write path explicit, avoids ORM lifecycle semantics for immutable audit events, and preserves the Audit module's infrastructure independence.

## Revisit when

Revisit this decision when product requirements define audit retention, searchable audit history, legal/compliance export, cryptographic tamper evidence, provider/model attribution, or conversation persistence.
