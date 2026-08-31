# 0012 — Use an external gRPC Mock ERP for evidence workloads

## Status

Accepted

## Context

The Evidence Lab needs deterministic source workloads while preserving its black-box trust boundary.
The previous in-process `mock-erp` adapter owned fixture data, source pagination, cursors, and failure
scenarios inside `platform-service`. Using that implementation for evidence would make the system
under test partially responsible for generating the workload used to prove its own behavior.

Connector Management already defines `BusinessDataSource` as an application-owned boundary for
communicating with external systems. The `mock-erp` connector should therefore behave like an
external integration rather than an in-memory fixture.

## Decision

`mock-erp` is an external gRPC source.

- The EOC-facing source contract lives at `contracts/mock-erp/v1/source/mock_erp_source.proto`;
  `platform-service` compiles only this contract.
- The evidence-only control contract lives at `tools/mock-erp/contracts/mock_erp_control.proto` and is
  not part of the Platform Service protobuf build.
- `platform-service` contains only the outbound gRPC adapter and source-specific mapping.
- Deterministic dataset generation and source-owned pagination live under `tools/mock-erp`.
- The Mock ERP source service is reachable by EOC on the Compose platform network.
- An evidence-only control service binds to loopback inside the Mock ERP container and is invoked by
  the Evidence Lab through `docker compose exec`; EOC cannot call the control service.
- Seed, record count, benchmark terminology, and fault controls must not appear in EOC business
  modules or normal runtime behavior.
- Source page tokens and incremental cursors are opaque to EOC.
- The old in-process Mock ERP fixture/scenario implementation is removed. Transport-independent
  application tests use a small `BusinessDataSource` test double that does not model Mock ERP
  transport behavior.
- PR73 uses unary RPCs only. Streaming, source authentication, and generalized connector credential
  resolution remain out of scope.

## Consequences

The baseline exercises a real process and network boundary before Operations, Kafka, and Analytics.
Later source-side reliability campaigns can inject latency, unavailable responses, malformed source
positions, and other failures externally without adding evidence switches to EOC.

The local evidence environment has one additional container and protobuf/gRPC build tooling. Normal
Compose startup remains unchanged because the simulator is added only through the evidence Compose
overlay.
