package io.github.khaledshawki.eoc.operations.application.model.outbox;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventEnvelope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimedOperationsOutboxEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    UUID tenantId,
    String aggregateType,
    UUID aggregateId,
    long aggregateVersion,
    String payload,
    Instant occurredAt,
    int publicationAttempt,
    int recoveryGeneration,
    int generationAttempt,
    String claimOwner,
    Instant claimedAt) {

  public ClaimedOperationsOutboxEvent {
    OperationsIntegrationEventEnvelope envelope =
        new OperationsIntegrationEventEnvelope(
            eventId,
            eventType,
            schemaVersion,
            tenantId,
            aggregateType,
            aggregateId,
            aggregateVersion,
            payload,
            occurredAt);
    eventId = envelope.eventId();
    eventType = envelope.eventType();
    schemaVersion = envelope.schemaVersion();
    tenantId = envelope.tenantId();
    aggregateType = envelope.aggregateType();
    aggregateId = envelope.aggregateId();
    aggregateVersion = envelope.aggregateVersion();
    payload = envelope.payload();
    occurredAt = envelope.occurredAt();

    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    if (recoveryGeneration < 0) {
      throw new IllegalArgumentException("Recovery generation cannot be negative");
    }
    if (generationAttempt < 1 || generationAttempt > publicationAttempt) {
      throw new IllegalArgumentException(
          "Generation attempt must be positive and cannot exceed the publication attempt");
    }
    claimOwner = OperationsOutboxClaim.requireClaimOwner(claimOwner);
    Objects.requireNonNull(claimedAt, "Claim timestamp cannot be null");
    if (claimedAt.isBefore(occurredAt)) {
      throw new IllegalArgumentException("Claim timestamp cannot precede event occurrence");
    }
  }

  public ClaimedOperationsOutboxEvent(
      UUID eventId,
      String eventType,
      int schemaVersion,
      UUID tenantId,
      String aggregateType,
      UUID aggregateId,
      long aggregateVersion,
      String payload,
      Instant occurredAt,
      int publicationAttempt,
      String claimOwner,
      Instant claimedAt) {
    this(
        eventId,
        eventType,
        schemaVersion,
        tenantId,
        aggregateType,
        aggregateId,
        aggregateVersion,
        payload,
        occurredAt,
        publicationAttempt,
        0,
        publicationAttempt,
        claimOwner,
        claimedAt);
  }

  public OperationsIntegrationEventEnvelope integrationEvent() {
    return new OperationsIntegrationEventEnvelope(
        eventId,
        eventType,
        schemaVersion,
        tenantId,
        aggregateType,
        aggregateId,
        aggregateVersion,
        payload,
        occurredAt);
  }
}
