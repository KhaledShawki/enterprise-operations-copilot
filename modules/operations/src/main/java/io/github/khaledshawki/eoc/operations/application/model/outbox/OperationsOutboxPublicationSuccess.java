package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationsOutboxPublicationSuccess(
    UUID eventId, String claimOwner, int publicationAttempt, Instant publishedAt) {

  public OperationsOutboxPublicationSuccess {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    claimOwner = OperationsOutboxClaim.requireClaimOwner(claimOwner);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    Objects.requireNonNull(publishedAt, "Publication timestamp cannot be null");
  }
}
