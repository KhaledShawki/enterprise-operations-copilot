package io.github.khaledshawki.eoc.analytics.application.port.in;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionResult;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;

public interface ConsumeAnalyticsIntegrationEventUseCase {

  AnalyticsEventConsumptionResult consume(AnalyticsIntegrationEvent event);
}
