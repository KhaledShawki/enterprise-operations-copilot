package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CopilotCustomer(
    UUID customerId,
    boolean projected,
    Optional<String> partnerNumber,
    Optional<String> displayName) {
  public CopilotCustomer {
    Objects.requireNonNull(customerId, "Copilot customer id cannot be null");
    Objects.requireNonNull(partnerNumber, "Copilot customer partner number cannot be null");
    Objects.requireNonNull(displayName, "Copilot customer display name cannot be null");
    if (partnerNumber.isPresent() != displayName.isPresent()
        || projected != partnerNumber.isPresent()) {
      throw new IllegalArgumentException("Copilot customer projection fields are inconsistent");
    }
    partnerNumber = partnerNumber.map(value -> requiredText(value, "partner number"));
    displayName = displayName.map(value -> requiredText(value, "display name"));
  }

  private static String requiredText(String value, String field) {
    Objects.requireNonNull(value, "Copilot customer " + field + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Copilot customer " + field + " cannot be blank");
    }
    return normalized;
  }
}
