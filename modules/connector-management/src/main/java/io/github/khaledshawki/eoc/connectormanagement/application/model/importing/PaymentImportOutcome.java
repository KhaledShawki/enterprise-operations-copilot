package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import java.util.Objects;
import java.util.UUID;

/** Durable classification returned after a downstream payment page transaction commits. */
public record PaymentImportOutcome(
    UUID pageAcceptanceId, long fetched, long accepted, long rejected, long duplicates) {

  public PaymentImportOutcome {
    Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
    if (fetched < 0 || accepted < 0 || rejected < 0 || duplicates < 0) {
      throw new IllegalArgumentException("Payment import counts cannot be negative");
    }
    if (fetched != Math.addExact(Math.addExact(accepted, rejected), duplicates)) {
      throw new IllegalArgumentException(
          "Fetched records must equal accepted, rejected, and duplicate records");
    }
  }
}
