# Enterprise Operations Copilot

[![CI](https://github.com/KhaledShawki/enterprise-operations-copilot/actions/workflows/ci.yml/badge.svg)](https://github.com/KhaledShawki/enterprise-operations-copilot/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Enterprise Operations Copilot brings operational data from business systems into one place. It is designed to import data reliably, surface operational risks, and answer business questions using traceable evidence.

The first workflow focuses on customers and invoices: importing records from an external source, identifying overdue receivables, and querying the result through a permission-aware copilot.

## Architecture

The backend follows domain-driven design and hexagonal architecture, starting as a modular Java platform and evolving into a distributed system of independently deployable microservices as its service boundaries, scaling needs, and failure-isolation requirements are proven.

The distributed architecture is designed around:

- tenant isolation and explicit authorization
- reliable and idempotent connector ingestion
- transactional outbox and idempotent consumers
- Kafka-based event distribution
- event-driven projections and reconciliation
- observable failure and recovery behavior
- deterministic AI tools for structured business facts

Planned service boundaries include tenant access, connector management, operations, analytics, document processing, audit, and copilot orchestration.

## Technology

The initial foundation uses Java 25, Spring Boot, Maven, PostgreSQL, Keycloak, Angular, and Docker Compose.

The architecture is designed to evolve with Kafka-compatible event streaming, a Go connector worker, Python-based AI services, Terraform, and Google Cloud as those capabilities are introduced.

## Local development

The complete local platform runs with Docker Compose and includes the platform service,
its PostgreSQL database, Keycloak, and Keycloak's PostgreSQL database.

See the [Local Platform runbook](docs/runbooks/local-platform.md) for configuration,
startup, health verification, migration checks, persistence behavior, and reset
instructions. For identity-provider-only operation, use the
[Local Keycloak runbook](docs/runbooks/local-keycloak.md).

## Documentation

Architecture decisions, system design, and operational runbooks are maintained under [`docs`](docs).

## License

Licensed under the [MIT License](LICENSE).
