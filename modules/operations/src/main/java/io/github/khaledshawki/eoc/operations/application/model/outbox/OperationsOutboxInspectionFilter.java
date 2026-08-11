package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public record OperationsOutboxInspectionFilter(
    Optional<OperationsOutboxStatus> status,
    Optional<UUID> tenantId,
    Optional<String> aggregateType,
    Optional<UUID> aggregateId,
    Optional<OperationsOutboxCursor> cursor,
    int limit) {

  public static final int MAX_LIMIT = 100;
  private static final Pattern AGGREGATE_TYPE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public OperationsOutboxInspectionFilter {
    Objects.requireNonNull(status, "Operations outbox status filter cannot be null");
    Objects.requireNonNull(tenantId, "Operations outbox tenant filter cannot be null");
    Objects.requireNonNull(aggregateType, "Operations outbox aggregate type filter cannot be null");
    Objects.requireNonNull(aggregateId, "Operations outbox aggregate id filter cannot be null");
    Objects.requireNonNull(cursor, "Operations outbox cursor cannot be null");
    aggregateType = aggregateType.map(OperationsOutboxInspectionFilter::normalizeAggregateType);
    if (aggregateId.isPresent() && aggregateType.isEmpty()) {
      throw new IllegalArgumentException(
          "Operations outbox aggregate type is required when aggregate id is provided");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException(
          "Operations outbox inspection limit must be between 1 and 100");
    }
  }

  private static String normalizeAggregateType(String aggregateType) {
    Objects.requireNonNull(aggregateType, "Operations outbox aggregate type cannot be null");
    String normalized = aggregateType.strip().toUpperCase(Locale.ROOT);
    if (!AGGREGATE_TYPE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Operations outbox aggregate type has an invalid format");
    }
    return normalized;
  }
}
