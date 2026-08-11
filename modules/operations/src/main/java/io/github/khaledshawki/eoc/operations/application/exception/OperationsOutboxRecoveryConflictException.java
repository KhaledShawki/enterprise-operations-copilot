package io.github.khaledshawki.eoc.operations.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class OperationsOutboxRecoveryConflictException extends RuntimeException {

  private final UUID eventId;

  public OperationsOutboxRecoveryConflictException(UUID eventId, String detail) {
    super(
        Objects.requireNonNull(
            detail, "Operations outbox recovery conflict detail cannot be null"));
    this.eventId =
        Objects.requireNonNull(eventId, "Operations outbox recovery event id cannot be null");
  }

  public UUID eventId() {
    return eventId;
  }
}
