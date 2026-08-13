# ADR 0007: Expose Copilot tools through a stateless MCP inbound adapter

## Status

Accepted

## Context

The Copilot module owns a deterministic, permission-aware tool boundary for receivables.
That boundary is intentionally independent of LLM providers, HTTP transports, Spring, and
MCP.

External AI clients should be able to discover and invoke the approved Copilot capabilities
through a standard protocol without making MCP part of the Copilot application contract.

MCP requests also need the same security model as the rest of the platform. Authentication
comes from the existing OAuth2 resource server, while tenant authorization remains
authoritative in the application database. The model must not be allowed to choose a tenant by
supplying a `tenantId` tool argument.

The first Copilot tools are synchronous, read-only request/response operations. They do not
need server-to-client sampling, elicitation, subscriptions, or transport session state.

## Decision

The platform service exposes Copilot through a Spring AI 2.0 WebMVC MCP server as an inbound
adapter.

The MCP server uses stateless Streamable HTTP at `/mcp`. It is disabled by default and is
enabled explicitly with `EOC_COPILOT_MCP_ENABLED=true`.

Only these three tools are registered:

- `get_receivable`
- `list_receivables`
- `get_receivables_summary`

The server does not expose MCP resources, prompts, or completion capabilities. Generic Spring
AI `ToolCallback` conversion and MCP annotation scanning are disabled. The platform explicitly
builds the MCP tool specifications from the single Copilot MCP adapter so unrelated tool beans
cannot become externally reachable by accident.

The MCP adapter delegates exclusively to `ExecuteCopilotToolUseCase`. It does not call
Analytics, Tenant Access, persistence, Kafka, or other bounded contexts directly.

MCP is not allowed inside `modules/copilot`. MCP and Spring AI types remain Platform
infrastructure concerns.

Authentication is performed by the existing Spring Security OAuth2 resource server. The
stateless transport carries the authenticated JWT principal into the MCP transport context.

The selected tenant is supplied out of band in the `X-EOC-Tenant-Id` HTTP header. It is never
part of an MCP tool input schema. The adapter combines the authenticated JWT `iss`/`sub` with
that selected tenant to create the existing `CopilotExecutionContext`. The existing Copilot
authorization port then checks current tenant membership and role data before any Analytics
read.

Supplying a tenant header is therefore only selecting a target tenant. It is not an
authorization grant.

The MCP HTTP transport validates `Host` and `Origin` headers to reduce DNS-rebinding and
cross-origin exposure. Allowed hosts and origins are deployment configuration.

All exposed tools declare read-only, idempotent, closed-world MCP hints. Tool outputs are
transport-owned structured records that preserve receivable source evidence and
currency-separated financial values.

Transport failures map to stable MCP-safe error messages and do not expose internal exception
details.

## Alternatives considered

### Put MCP types in the Copilot module

Rejected because MCP is a transport concern. The deterministic Copilot boundary must remain
usable by in-process orchestration and future transports without an MCP dependency.

### Use stateful Streamable HTTP

Rejected for the initial tools because they need no server-to-client requests or transport
session state. Stateless HTTP has a smaller operational and security surface.

### Put `tenantId` in each MCP tool argument

Rejected because tool arguments are model-controlled. Tenant selection belongs to trusted
request context and is independently authorized against current application membership data.

### Auto-expose all Spring AI tool callbacks

Rejected because adding an unrelated `ToolCallback` bean could silently expand the public MCP
surface. The approved MCP tool set is registered explicitly.

### Add an LLM provider in the same slice

Rejected because MCP exposure and LLM orchestration are separate concerns. Provider/model
selection, prompts, tool loops, and evidence-gated answer generation belong to the next slice.

## Consequences

MCP clients can discover and invoke the deterministic receivables capabilities without
changing the Copilot application module.

Deployments that enable MCP must configure allowed public host/origin patterns when the
defaults for local development are insufficient.

Every tool call requires a valid authenticated principal, an explicit selected tenant, and a
successful current tenant authorization check.

The platform gains a stable external tool protocol while retaining direct in-process use of
`ExecuteCopilotToolUseCase` for future orchestration.

The next Copilot slice may add LLM orchestration without making that orchestration depend on an
MCP network hop.
