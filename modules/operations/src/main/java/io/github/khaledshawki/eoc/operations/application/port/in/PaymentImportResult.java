package io.github.khaledshawki.eoc.operations.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentImportResult(
    UUID pageAcceptanceId,
    long fetchedCount,
    long createdCount,
    long updatedCount,
    long duplicateCount,
    long staleCount,
    Instant acceptedAt) {

  public PaymentImportResult {
    Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
    Objects.requireNonNull(acceptedAt, "Page acceptance timestamp cannot be null");
    if (fetchedCount < 0
        || createdCount < 0
        || updatedCount < 0
        || duplicateCount < 0
        || staleCount < 0) {
      throw new IllegalArgumentException("Payment import counts cannot be negative");
    }
    long classifiedCount =
        Math.addExact(
            Math.addExact(createdCount, updatedCount), Math.addExact(duplicateCount, staleCount));
    if (fetchedCount != classifiedCount) {
      throw new IllegalArgumentException(
          "Fetched count must equal created, updated, duplicate, and stale counts");
    }
  }

  public long acceptedCount() {
    return Math.addExact(createdCount, updatedCount);
  }

  public long notAppliedCount() {
    return Math.addExact(duplicateCount, staleCount);
  }
}
