package io.github.khaledshawki.eoc.platform.operations.configuration;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.operations-outbox")
public record OperationsOutboxProperties(
    boolean relayEnabled,
    int batchSize,
    Duration claimLease,
    int maxAttempts,
    Duration retryDelay,
    long initialDelayMs,
    long fixedDelayMs) {

  public OperationsOutboxProperties {
    if (batchSize < 1 || batchSize > OperationsOutboxClaim.MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Operations outbox batch size must be between 1 and 100");
    }
    Objects.requireNonNull(claimLease, "Operations outbox claim lease cannot be null");
    if (claimLease.isZero()
        || claimLease.isNegative()
        || claimLease.compareTo(OperationsOutboxClaim.MAX_CLAIM_LEASE) > 0) {
      throw new IllegalArgumentException(
          "Operations outbox claim lease must be positive and at most one hour");
    }
    if (maxAttempts < 1 || maxAttempts > 100) {
      throw new IllegalArgumentException(
          "Operations outbox max attempts must be between 1 and 100");
    }
    Objects.requireNonNull(retryDelay, "Operations outbox retry delay cannot be null");
    if (retryDelay.isZero()
        || retryDelay.isNegative()
        || retryDelay.compareTo(Duration.ofDays(1)) > 0) {
      throw new IllegalArgumentException(
          "Operations outbox retry delay must be positive and at most one day");
    }
    if (initialDelayMs < 0) {
      throw new IllegalArgumentException("Operations outbox initial delay cannot be negative");
    }
    if (fixedDelayMs < 1) {
      throw new IllegalArgumentException("Operations outbox fixed delay must be positive");
    }
  }
}
