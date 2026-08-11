package io.github.khaledshawki.eoc.operations.application.model.outbox;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NewOperationsOutboxRecovery(
    UUID recoveryId, OperationsActor actor, UUID eventId, String reason, Instant requestedAt) {

  public NewOperationsOutboxRecovery {
    Objects.requireNonNull(recoveryId, "Operations outbox recovery id cannot be null");
    Objects.requireNonNull(actor, "Operations outbox recovery actor cannot be null");
    Objects.requireNonNull(eventId, "Operations outbox recovery event id cannot be null");
    Objects.requireNonNull(reason, "Operations outbox recovery reason cannot be null");
    reason = reason.strip();
    if (reason.isEmpty()) {
      throw new IllegalArgumentException("Operations outbox recovery reason cannot be blank");
    }
    if (reason.length() > 500) {
      throw new IllegalArgumentException(
          "Operations outbox recovery reason cannot exceed 500 characters");
    }
    Objects.requireNonNull(
        requestedAt, "Operations outbox recovery request timestamp cannot be null");
  }
}
