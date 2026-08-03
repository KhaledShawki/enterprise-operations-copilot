package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportFailurePayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunCompletedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunFailedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunRetryScheduledPayload;
import java.util.Objects;

final class ConnectorIntegrationEventPayloadSerializer {

  String serialize(ConnectorIntegrationEventPayload payload) {
    Objects.requireNonNull(payload, "Connector integration event payload cannot be null");
    return switch (payload) {
      case ImportRunCompletedPayload value -> completed(value);
      case ImportRunFailedPayload value -> failed(value);
      case ImportRunRetryScheduledPayload value -> retryScheduled(value);
    };
  }

  private static String completed(ImportRunCompletedPayload value) {
    return "{\"connectorId\":\""
        + value.connectorId()
        + "\",\"importType\":\""
        + value.importType()
        + "\",\"importMode\":\""
        + value.importMode()
        + "\",\"status\":\""
        + value.status()
        + "\",\"fetchedCount\":"
        + value.fetchedCount()
        + ",\"acceptedCount\":"
        + value.acceptedCount()
        + ",\"rejectedCount\":"
        + value.rejectedCount()
        + ",\"duplicateCount\":"
        + value.duplicateCount()
        + ",\"attemptCount\":"
        + value.attemptCount()
        + "}";
  }

  private static String failed(ImportRunFailedPayload value) {
    return "{\"connectorId\":\""
        + value.connectorId()
        + "\",\"importType\":\""
        + value.importType()
        + "\",\"importMode\":\""
        + value.importMode()
        + "\",\"failure\":"
        + failure(value.failure())
        + ",\"attemptCount\":"
        + value.attemptCount()
        + "}";
  }

  private static String retryScheduled(ImportRunRetryScheduledPayload value) {
    return "{\"connectorId\":\""
        + value.connectorId()
        + "\",\"importType\":\""
        + value.importType()
        + "\",\"importMode\":\""
        + value.importMode()
        + "\",\"failure\":"
        + failure(value.failure())
        + ",\"attemptCount\":"
        + value.attemptCount()
        + ",\"nextRetryAt\":\""
        + value.nextRetryAt()
        + "\"}";
  }

  private static String failure(ImportFailurePayload value) {
    return "{\"category\":\"" + value.category() + "\",\"code\":\"" + value.code() + "\"}";
  }
}
