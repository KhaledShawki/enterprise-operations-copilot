package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CopilotEvidence(UUID eventId, long aggregateVersion, Instant occurredAt) {
  public CopilotEvidence {
    Objects.requireNonNull(eventId, "Copilot evidence event id cannot be null");
    if (aggregateVersion < 1) {
      throw new IllegalArgumentException("Copilot evidence aggregate version must be positive");
    }
    Objects.requireNonNull(occurredAt, "Copilot evidence occurredAt cannot be null");
  }
}
