package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationsOutboxCursor(Instant createdAt, UUID eventId) {

  public OperationsOutboxCursor {
    Objects.requireNonNull(createdAt, "Operations outbox cursor timestamp cannot be null");
    Objects.requireNonNull(eventId, "Operations outbox cursor event id cannot be null");
  }
}
