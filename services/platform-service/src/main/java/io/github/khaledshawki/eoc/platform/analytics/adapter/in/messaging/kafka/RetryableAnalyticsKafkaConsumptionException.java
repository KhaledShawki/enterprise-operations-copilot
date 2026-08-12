package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

final class RetryableAnalyticsKafkaConsumptionException extends AnalyticsKafkaConsumptionException {

  RetryableAnalyticsKafkaConsumptionException(String failureCode, Throwable cause) {
    super(failureCode, true, cause);
  }
}
