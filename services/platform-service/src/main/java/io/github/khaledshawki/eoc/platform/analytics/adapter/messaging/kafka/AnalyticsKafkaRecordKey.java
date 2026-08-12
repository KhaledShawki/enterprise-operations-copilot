package io.github.khaledshawki.eoc.platform.analytics.adapter.messaging.kafka;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import java.util.Objects;

public final class AnalyticsKafkaRecordKey {

  private AnalyticsKafkaRecordKey() {}

  public static String from(AnalyticsIntegrationEvent event) {
    Objects.requireNonNull(event, "Analytics integration event cannot be null");
    return event.tenantId() + ":" + event.aggregateType() + ":" + event.aggregateId();
  }
}
