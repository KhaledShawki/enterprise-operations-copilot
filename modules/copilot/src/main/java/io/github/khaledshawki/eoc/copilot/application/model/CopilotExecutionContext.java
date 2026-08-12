package io.github.khaledshawki.eoc.copilot.application.model;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record CopilotExecutionContext(URI issuer, String subject, UUID tenantId) {
  public CopilotExecutionContext {
    Objects.requireNonNull(issuer, "Copilot issuer cannot be null");
    if (!issuer.isAbsolute()) {
      throw new IllegalArgumentException("Copilot issuer must be an absolute URI");
    }
    Objects.requireNonNull(subject, "Copilot subject cannot be null");
    subject = subject.strip();
    if (subject.isEmpty()) {
      throw new IllegalArgumentException("Copilot subject cannot be blank");
    }
    Objects.requireNonNull(tenantId, "Copilot tenant id cannot be null");
  }
}
