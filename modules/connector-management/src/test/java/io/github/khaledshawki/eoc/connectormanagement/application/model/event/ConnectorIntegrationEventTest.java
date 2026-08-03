package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorIntegrationEventTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID IMPORT_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID CONNECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T10:15:30Z");

  @Test
  void shouldExposeStableVersionedMetadataForCompletion() {
    ImportRunCompletedPayload payload =
        new ImportRunCompletedPayload(
            CONNECTOR_ID, "CUSTOMERS", "INCREMENTAL", "COMPLETED", 3, 2, 0, 1, 1);

    ConnectorIntegrationEvent event =
        new ConnectorIntegrationEvent(
            EVENT_ID,
            ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED,
            TENANT_ID,
            "IMPORT_RUN",
            IMPORT_RUN_ID,
            OCCURRED_AT,
            payload);

    assertEquals("connector.import-run.completed.v1", event.eventType());
    assertEquals(1, event.schemaVersion());
    assertEquals("IMPORT_RUN", event.aggregateType());
    assertEquals(payload, event.payload());
  }

  @Test
  void shouldSupportFailureAndRetryContracts() {
    ImportFailurePayload failure = new ImportFailurePayload("TIMEOUT", "source-timeout");

    ConnectorIntegrationEvent failed =
        new ConnectorIntegrationEvent(
            EVENT_ID,
            ConnectorIntegrationEventType.IMPORT_RUN_FAILED,
            TENANT_ID,
            "IMPORT_RUN",
            IMPORT_RUN_ID,
            OCCURRED_AT,
            new ImportRunFailedPayload(CONNECTOR_ID, "CUSTOMERS", "FULL", failure, 3));
    ConnectorIntegrationEvent retry =
        new ConnectorIntegrationEvent(
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED,
            TENANT_ID,
            "IMPORT_RUN",
            IMPORT_RUN_ID,
            OCCURRED_AT,
            new ImportRunRetryScheduledPayload(
                CONNECTOR_ID, "CUSTOMERS", "INCREMENTAL", failure, 2, OCCURRED_AT.plusSeconds(60)));

    assertEquals("connector.import-run.failed.v1", failed.eventType());
    assertEquals("connector.import-run.retry-scheduled.v1", retry.eventType());
  }

  @Test
  void shouldRejectMismatchedPayloadType() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorIntegrationEvent(
                EVENT_ID,
                ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED,
                TENANT_ID,
                "IMPORT_RUN",
                IMPORT_RUN_ID,
                OCCURRED_AT,
                new ImportRunFailedPayload(
                    CONNECTOR_ID,
                    "CUSTOMERS",
                    "FULL",
                    new ImportFailurePayload("TIMEOUT", "source-timeout"),
                    1)));
  }

  @Test
  void shouldRejectAggregateTypeThatDoesNotMatchTheContract() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorIntegrationEvent(
                EVENT_ID,
                ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED,
                TENANT_ID,
                "CONNECTOR",
                IMPORT_RUN_ID,
                OCCURRED_AT,
                completedPayload()));
  }

  @Test
  void shouldRejectRetryAtOrBeforeOccurrence() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ConnectorIntegrationEvent(
                EVENT_ID,
                ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED,
                TENANT_ID,
                "IMPORT_RUN",
                IMPORT_RUN_ID,
                OCCURRED_AT,
                new ImportRunRetryScheduledPayload(
                    CONNECTOR_ID,
                    "CUSTOMERS",
                    "INCREMENTAL",
                    new ImportFailurePayload("TIMEOUT", "source-timeout"),
                    1,
                    OCCURRED_AT)));
  }

  @Test
  void shouldRejectInconsistentCompletionStatistics() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImportRunCompletedPayload(
                CONNECTOR_ID, "CUSTOMERS", "FULL", "COMPLETED", 3, 1, 0, 1, 1));
  }

  @Test
  void shouldRejectInvalidFailureCode() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ImportFailurePayload("TIMEOUT", "Source Timeout"));
  }

  private static ImportRunCompletedPayload completedPayload() {
    return new ImportRunCompletedPayload(
        CONNECTOR_ID, "CUSTOMERS", "FULL", "COMPLETED", 1, 1, 0, 0, 1);
  }
}
