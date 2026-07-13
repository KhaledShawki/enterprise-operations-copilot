# ADR 0001: Adopt an evolutionary modular architecture

## Status

Accepted

## Context

Enterprise Operations Copilot is expected to grow across multiple business
capabilities, including tenant access, connector management, operations,
analytics, document processing, audit, and copilot orchestration.

Starting with independently deployed microservices would introduce distributed
transactions, network failure modes, deployment coordination, observability
requirements, and higher infrastructure costs before service boundaries and
scaling characteristics are proven.

A single unstructured application would be simpler initially but would make
future extraction difficult.

## Decision

The backend starts as a modular Java platform with explicit bounded contexts.
These modules are initially deployed together through `platform-service`.

Each bounded context:

- owns its domain model and application use cases
- follows domain-driven design and hexagonal architecture
- communicates with other modules through explicit interfaces
- keeps persistence and transport concerns outside its application core
- avoids direct access to another context's database tables
- exposes boundaries that can be verified through automated architecture tests

Capabilities will be extracted into independently deployed services when
supported by evidence such as:

- independent scaling requirements
- different availability or failure-isolation requirements
- independent release cadence
- clear ownership boundaries
- technology requirements that justify a separate runtime

## Consequences

The initial system is easier to develop, test, deploy, and operate.

Module boundaries require active enforcement. Some in-process communication may
later be replaced by APIs or asynchronous events during service extraction.

The architecture supports future distribution without accepting unnecessary
distributed-system complexity at the beginning.
