package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

final class TerminalAnalyticsKafkaConsumptionException extends AnalyticsKafkaConsumptionException {

  TerminalAnalyticsKafkaConsumptionException(String failureCode, Throwable cause) {
    super(failureCode, false, cause);
  }
}
