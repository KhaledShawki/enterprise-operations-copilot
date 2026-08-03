package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConnectorOutboxPublicationSuccess(
    UUID eventId, String claimOwner, int publicationAttempt, Instant publishedAt) {

  public ConnectorOutboxPublicationSuccess {
    Objects.requireNonNull(eventId, "Outbox event id cannot be null");
    claimOwner = ConnectorOutboxClaim.requireClaimOwner(claimOwner);
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    Objects.requireNonNull(publishedAt, "Publication timestamp cannot be null");
  }
}
