package io.github.khaledshawki.eoc.operations.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class OperationsOutboxEventNotFoundException extends RuntimeException {

  private final UUID eventId;

  public OperationsOutboxEventNotFoundException(UUID eventId) {
    super("Operations outbox event not found: " + Objects.requireNonNull(eventId));
    this.eventId = eventId;
  }

  public UUID eventId() {
    return eventId;
  }
}
