package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

public enum ConnectorIntegrationEventType {
  IMPORT_RUN_COMPLETED(
      "connector.import-run.completed.v1", 1, "IMPORT_RUN", ImportRunCompletedPayload.class),
  IMPORT_RUN_FAILED(
      "connector.import-run.failed.v1", 1, "IMPORT_RUN", ImportRunFailedPayload.class),
  IMPORT_RUN_RETRY_SCHEDULED(
      "connector.import-run.retry-scheduled.v1",
      1,
      "IMPORT_RUN",
      ImportRunRetryScheduledPayload.class);

  private final String eventType;
  private final int schemaVersion;
  private final String aggregateType;
  private final Class<? extends ConnectorIntegrationEventPayload> payloadType;

  ConnectorIntegrationEventType(
      String eventType,
      int schemaVersion,
      String aggregateType,
      Class<? extends ConnectorIntegrationEventPayload> payloadType) {
    this.eventType = eventType;
    this.schemaVersion = schemaVersion;
    this.aggregateType = aggregateType;
    this.payloadType = payloadType;
  }

  public String eventType() {
    return eventType;
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public String aggregateType() {
    return aggregateType;
  }

  boolean supports(ConnectorIntegrationEventPayload payload) {
    return payloadType.isInstance(payload);
  }
}
