package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record ImportRunCompletedPayload(
    UUID connectorId,
    String importType,
    String importMode,
    String status,
    long fetchedCount,
    long acceptedCount,
    long rejectedCount,
    long duplicateCount,
    int attemptCount)
    implements ConnectorIntegrationEventPayload {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
  private static final Set<String> COMPLETION_STATUSES = Set.of("COMPLETED", "PARTIALLY_COMPLETED");

  public ImportRunCompletedPayload {
    Objects.requireNonNull(connectorId, "Connector id cannot be null");
    importType = requireCode(importType, "Import type");
    importMode = requireCode(importMode, "Import mode");
    status = requireCode(status, "Import status");
    if (!COMPLETION_STATUSES.contains(status)) {
      throw new IllegalArgumentException("Completion event status is not terminal completion");
    }
    requireNonNegative(fetchedCount, "Fetched count");
    requireNonNegative(acceptedCount, "Accepted count");
    requireNonNegative(rejectedCount, "Rejected count");
    requireNonNegative(duplicateCount, "Duplicate count");
    if (attemptCount < 1) {
      throw new IllegalArgumentException("Attempt count must be positive");
    }

    long accounted;
    try {
      accounted = Math.addExact(Math.addExact(acceptedCount, rejectedCount), duplicateCount);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Import statistics overflow", exception);
    }
    if (fetchedCount != accounted) {
      throw new IllegalArgumentException("Fetched count must equal all classified records");
    }
  }

  private static String requireCode(String value, String field) {
    Objects.requireNonNull(value, field + " cannot be null");
    if (!CODE.matcher(value).matches()) {
      throw new IllegalArgumentException(field + " must be an uppercase contract code");
    }
    return value;
  }

  private static void requireNonNegative(long value, String field) {
    if (value < 0) {
      throw new IllegalArgumentException(field + " cannot be negative");
    }
  }
}
