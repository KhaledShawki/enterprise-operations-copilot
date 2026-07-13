# ADR 0002: Minimize shared domain abstractions

## Status

Accepted

## Context

The system is expected to contain multiple bounded contexts implemented with
domain-driven design and hexagonal architecture. Shared abstractions can improve
consistency, but they also couple contexts and can become a generic framework.

## Decision

Domain models, repository ports, commands, results, exceptions, and event
payloads remain owned by their bounded contexts.

A small shared module may be introduced when at least two contexts demonstrate
the same stable technical requirement. Domain-event infrastructure is the most
likely first candidate.

Generic base entities, generic repositories, and generic CRUD services will not
be introduced.

## Consequences

Bounded contexts retain autonomy and explicit domain language. Some small
amount of duplication is accepted until a stable abstraction is proven.
