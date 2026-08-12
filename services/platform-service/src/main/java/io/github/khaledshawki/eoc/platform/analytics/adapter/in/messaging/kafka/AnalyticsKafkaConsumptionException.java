package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import java.util.Objects;

abstract class AnalyticsKafkaConsumptionException extends RuntimeException {

  private final String failureCode;
  private final boolean retryable;

  AnalyticsKafkaConsumptionException(String failureCode, boolean retryable, Throwable cause) {
    super(Objects.requireNonNull(failureCode, "Kafka failure code cannot be null"), cause);
    this.failureCode = failureCode;
    this.retryable = retryable;
  }

  final String failureCode() {
    return failureCode;
  }

  final boolean retryable() {
    return retryable;
  }
}
