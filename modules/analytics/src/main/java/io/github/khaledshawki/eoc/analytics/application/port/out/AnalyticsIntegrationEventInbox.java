package io.github.khaledshawki.eoc.analytics.application.port.out;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsInboxAcceptance;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;

public interface AnalyticsIntegrationEventInbox {

  AnalyticsInboxAcceptance accept(AnalyticsIntegrationEvent event);
}
