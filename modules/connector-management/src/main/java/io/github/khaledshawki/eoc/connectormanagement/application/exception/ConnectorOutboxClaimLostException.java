package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class ConnectorOutboxClaimLostException extends RuntimeException {

  private final UUID eventId;
  private final String claimOwner;
  private final int publicationAttempt;

  public ConnectorOutboxClaimLostException(
      UUID eventId, String claimOwner, int publicationAttempt) {
    super("Connector outbox event is no longer owned by the requesting publication claim");
    this.eventId = Objects.requireNonNull(eventId, "Outbox event id cannot be null");
    this.claimOwner = Objects.requireNonNull(claimOwner, "Claim owner cannot be null");
    if (publicationAttempt < 1) {
      throw new IllegalArgumentException("Publication attempt must be positive");
    }
    this.publicationAttempt = publicationAttempt;
  }

  public UUID eventId() {
    return eventId;
  }

  public String claimOwner() {
    return claimOwner;
  }

  public int publicationAttempt() {
    return publicationAttempt;
  }
}
