package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationsOutboxPublicationFailure(
    UUID eventId,
    String claimOwner,
    int publicationAttempt,
    String failureCode,
    Instant recordedAt) {

  public OperationsOutboxPublicationFailure {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    claimOwner = OperationsOutboxClaim.requireClaimOwner(claimOwner);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    failureCode = OperationsOutboxPublicationRetry.requireFailureCode(failureCode);
    Objects.requireNonNull(recordedAt, "Failure recording timestamp cannot be null");
  }
}
