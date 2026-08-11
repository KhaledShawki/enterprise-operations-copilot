package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.connector-events.kafka.dead-letter-recovery")
public record ConnectorDeadLetterRecoveryProperties(
    boolean enabled,
    Duration inspectionTimeout,
    int maxPageSize,
    int maxReplayGeneration,
    int maxAttempts,
    Duration retryDelay,
    int batchSize,
    Duration claimLease) {

  public ConnectorDeadLetterRecoveryProperties {
    inspectionTimeout = requirePositive(inspectionTimeout, "DLT inspection timeout");
    if (maxPageSize < 1 || maxPageSize > 1000) {
      throw new IllegalArgumentException("DLT maximum page size must be between 1 and 1000");
    }
    if (maxReplayGeneration < 1 || maxReplayGeneration > 100) {
      throw new IllegalArgumentException("DLT maximum replay generation must be between 1 and 100");
    }
    if (maxAttempts < 1 || maxAttempts > 100) {
      throw new IllegalArgumentException("DLT replay max attempts must be between 1 and 100");
    }
    retryDelay = requirePositive(retryDelay, "DLT replay retry delay");
    if (batchSize < 1 || batchSize > 100) {
      throw new IllegalArgumentException("DLT replay batch size must be between 1 and 100");
    }
    claimLease = requirePositive(claimLease, "DLT replay claim lease");
  }

  private static Duration requirePositive(Duration value, String description) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException(description + " must be positive and at most one day");
    }
    return value;
  }
}
