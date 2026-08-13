# ADR 0008: Add provider-neutral LLM Copilot orchestration

## Status

Accepted

## Context

The Copilot module already exposes three deterministic, permission-aware receivables tools through
`ExecuteCopilotToolUseCase`. PR #64 additionally exposes those tools to external MCP clients through
a stateless Platform inbound adapter.

The next capability is an in-process LLM orchestrator that can interpret a user question, choose
among the approved tools, consume their results, and return an evidence-backed answer. The LLM must
not gain a second path to Analytics, persistence, tenant identity, or authorization, and internal
orchestration must not call the application's own MCP endpoint over HTTP.

Spring AI 2.0 supports caller-controlled tool execution through `ChatModel`: tool definitions are
sent to the model, but requested tool calls are returned to the caller rather than executed
automatically. This allows the application to own the loop, limits, validation, authorization, and
evidence handling.

A terminal model response that contains free-form business prose plus a citation is not a strong
enough grounding guarantee. A model could cite a real tool call while still inventing an amount,
date, customer, status, currency, count, or aging value in its text.

## Decision

`modules/copilot` owns provider-neutral orchestration contracts and the orchestration state machine.
It adds:

- `AskCopilotUseCase`
- `CopilotModelPort`
- provider-neutral model requests, tool-call requests, observations, and responses
- a bounded `CopilotOrchestrationService`
- stable model/protocol/grounding/limit failure types

The Platform service owns the Spring AI adapter. Provider SDKs, Spring AI, JSON, configuration, and
model-specific concerns remain outside the pure Copilot module.

The orchestration loop calls `CopilotModelPort`, validates an entire requested tool-call round, and
then invokes the existing `ExecuteCopilotToolUseCase` directly with the trusted
`CopilotExecutionContext`. It never calls MCP or Analytics directly.

The initial loop is deliberately bounded to:

- 4 model rounds
- 6 tool calls in total
- 3 tool calls in one round
- at most 3 executed tool results selected for the final answer

Tool-call IDs must be unique. Unsupported tool names, repeated IDs, and exact repeated typed tool
requests fail closed before executing any part of the offending round.

Tenant identity is not included in the provider-neutral model request and is not part of any model
tool schema. The same trusted execution context is passed only to the deterministic tool use case,
where the existing database-backed Tenant Access authorization remains authoritative.

Business date is also not a model tool argument. `CopilotQuestion` carries an optional application-
selected business date. Orchestration rejects any model response that tries to populate a tool
request business date, then injects the trusted question date into the deterministic request. When
the question date is absent, the existing tool executor resolves it through its injected `Clock`.
This preserves historical-query support without letting the model choose accounting time. A future
inbound API must accept that date as a typed application request field rather than relying on LLM
date extraction.

The Spring AI adapter uses `ChatModel` directly with exactly three request-scoped, descriptor-only
`ToolCallback` definitions. The callbacks cannot execute business logic; invoking one directly
throws. Spring AI therefore describes the approved tool contract to the model, while the Copilot
application remains the only component that executes deterministic business tools.

The model may request:

- `get_receivable`
- `list_receivables`
- `get_receivables_summary`

Tool arguments are decoded strictly into the existing typed Copilot requests. Unknown fields,
malformed values, unsupported tools, oversized arguments, and oversized responses fail closed.
Neither `tenantId` nor `businessDate` exists in the model-facing schemas.

Tool results returned to the model omit tenant identity. Get/list results retain their source event
evidence. The deterministic summary has no per-event evidence today, so none is fabricated.

### Deterministic final business-fact rendering

The model does not generate the final business prose. When it has enough information, it returns
only a bounded JSON object selecting one to three executed tool-call IDs:

```json
{"groundingToolCallIds":["call-1"]}
```

The Copilot application verifies that every selected ID corresponds to an executed observation and
renders the final receivable, list, or summary text directly from the typed deterministic results.
The returned `CopilotAnswer` separately carries the selected tool name and source evidence.

This intentionally trades some stylistic freedom for a materially stronger first grounding
boundary: invoice amounts, statuses, dates, customers, currencies, counts, and aging figures in the
answer are application-rendered facts rather than model-generated claims.

A future slice may introduce richer natural-language synthesis only with a validation contract that
can preserve this grounding property.

### Provider configuration

OpenAI through Spring AI is the first Platform provider adapter, not an application dependency. LLM
orchestration and all model capabilities are disabled by default. Enabling orchestration without a
configured `ChatModel` fails startup rather than silently degrading to an ungrounded path.

Model calls have bounded tool arguments/results, total model input, model responses, an OpenAI
completion-token cap, an application-owned call timeout, a bounded concurrent-call permit count,
and bounded Spring AI retry attempts. Provider exceptions map to the stable Copilot
model-unavailable error without exposing provider text as the public error message.

No API key is stored in source control. The provider, model name, and API key are deployment
environment configuration.

## Alternatives considered

### Use `ChatClient` with its automatic tool loop

Rejected for this slice because the application must own exact round/call limits, repeated-call
rejection, trusted-context handling, and evidence accumulation.

### Execute the application's own MCP endpoint

Rejected because this is an in-process use case. Self-HTTP would add transport coupling, duplicate
authentication, network failure modes, and unnecessary latency.

### Let Spring AI tool callbacks execute the deterministic tools

Rejected because provider/tool infrastructure would then own the business execution path. The
callbacks are descriptor-only and intentionally non-executable.

### Return free-form model prose with citations

Rejected for the initial implementation because citation presence alone cannot prove that each
financial fact in the prose came from the cited result.

### Add Python, RAG, vectors, conversations, or AI persistence now

Rejected because none is required to prove the orchestration boundary. Those capabilities remain
separate future slices with their own requirements.

## Consequences

The LLM can interpret questions and choose bounded deterministic tools without receiving trusted
tenant identity or bypassing authorization.

Provider replacement is localized to Platform adapters and configuration. Unit tests can use a fake
`CopilotModelPort` or fake `ChatModel`; normal CI needs no live external provider.

Business-fact hallucination risk is reduced structurally because the final financial facts are
rendered by the application from validated tool observations. The first answer style is therefore
more deterministic and less conversational than a free-form LLM response.

There are no database, Flyway, Kafka, Operations, Analytics schema, MCP transport, RAG, or
conversation-persistence changes in this slice.
