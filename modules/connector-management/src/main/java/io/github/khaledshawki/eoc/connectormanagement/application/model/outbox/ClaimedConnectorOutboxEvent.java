package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimedConnectorOutboxEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    String payload,
    Instant occurredAt,
    int publicationAttempt,
    String claimOwner,
    Instant claimedAt) {

  public ClaimedConnectorOutboxEvent {
    ConnectorIntegrationEventEnvelope envelope =
        new ConnectorIntegrationEventEnvelope(
            eventId,
            eventType,
            schemaVersion,
            tenantId,
            aggregateType,
            aggregateId,
            payload,
            occurredAt);
    eventId = envelope.eventId();
    eventType = envelope.eventType();
    schemaVersion = envelope.schemaVersion();
    tenantId = envelope.tenantId();
    aggregateType = envelope.aggregateType();
    aggregateId = envelope.aggregateId();
    payload = envelope.payload();
    occurredAt = envelope.occurredAt();

    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    claimOwner = ConnectorOutboxClaim.requireClaimOwner(claimOwner);
    Objects.requireNonNull(claimedAt, "Claim timestamp cannot be null");
    if (claimedAt.isBefore(occurredAt)) {
      throw new IllegalArgumentException("Claim timestamp cannot precede event occurrence");
    }
  }

  public ConnectorIntegrationEventEnvelope integrationEvent() {
    return new ConnectorIntegrationEventEnvelope(
        eventId,
        eventType,
        schemaVersion,
        tenantId,
        aggregateType,
        aggregateId,
        payload,
        occurredAt);
  }
}
