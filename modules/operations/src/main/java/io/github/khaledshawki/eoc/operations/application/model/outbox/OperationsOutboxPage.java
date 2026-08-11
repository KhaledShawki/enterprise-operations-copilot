package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OperationsOutboxPage(
    List<OperationsOutboxEventView> events, Optional<OperationsOutboxCursor> nextCursor) {

  public OperationsOutboxPage {
    Objects.requireNonNull(events, "Operations outbox page events cannot be null");
    if (events.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Operations outbox page cannot contain null events");
    }
    events = List.copyOf(events);
    Objects.requireNonNull(nextCursor, "Operations outbox next cursor cannot be null");
  }
}
