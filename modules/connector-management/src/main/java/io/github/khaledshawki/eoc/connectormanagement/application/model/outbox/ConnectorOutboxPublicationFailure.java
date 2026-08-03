package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorOutboxPublicationFailure(
    UUID eventId,
    String claimOwner,
    int publicationAttempt,
    String failureCode,
    Instant recordedAt) {

  public ConnectorOutboxPublicationFailure {
    Objects.requireNonNull(eventId, "Outbox event id cannot be null");
    claimOwner = ConnectorOutboxClaim.requireClaimOwner(claimOwner);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    failureCode = ConnectorOutboxPublicationRetry.requireFailureCode(failureCode);
    Objects.requireNonNull(recordedAt, "Failure recording timestamp cannot be null");
  }
}
