package io.github.khaledshawki.eoc.analytics.application.model.querying;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReceivableCustomerSummary(
    UUID customerId, Optional<String> partnerNumber, Optional<String> displayName) {

  public ReceivableCustomerSummary {
    Objects.requireNonNull(customerId, "Receivable customer id cannot be null");
    Objects.requireNonNull(partnerNumber, "Receivable customer partner number cannot be null");
    Objects.requireNonNull(displayName, "Receivable customer display name cannot be null");
    partnerNumber = partnerNumber.map(value -> requiredText(value, "partner number"));
    displayName = displayName.map(value -> requiredText(value, "display name"));
    if (partnerNumber.isPresent() != displayName.isPresent()) {
      throw new IllegalArgumentException(
          "Receivable customer projection fields must be present or absent together");
    }
  }

  public boolean projected() {
    return partnerNumber.isPresent();
  }

  private static String requiredText(String value, String field) {
    Objects.requireNonNull(value, "Receivable customer " + field + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Receivable customer " + field + " cannot be blank");
    }
    return normalized;
  }
}
