package io.github.khaledshawki.eoc.connectormanagement.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorIntegrationEventEnvelopeTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T18:00:00Z");

  @Test
  void shouldExtractOnlyImmutableDeliveryDataFromAClaimedOutboxEvent() {
    ClaimedConnectorOutboxEvent claimed =
        new ClaimedConnectorOutboxEvent(
            EVENT_ID,
            "connector.import-run.completed.v1",
            1,
            TENANT_ID,
            "IMPORT_RUN",
            AGGREGATE_ID,
            "{\"status\":\"COMPLETED\"}",
            OCCURRED_AT,
            4,
            "worker-a",
            OCCURRED_AT.plusSeconds(10));

    ConnectorIntegrationEventEnvelope envelope = claimed.integrationEvent();

    assertEquals(EVENT_ID, envelope.eventId());
    assertEquals("connector.import-run.completed.v1", envelope.eventType());
    assertEquals(1, envelope.schemaVersion());
    assertEquals(TENANT_ID, envelope.tenantId());
    assertEquals("IMPORT_RUN", envelope.aggregateType());
    assertEquals(AGGREGATE_ID, envelope.aggregateId());
    assertEquals("{\"status\":\"COMPLETED\"}", envelope.payload());
    assertEquals(OCCURRED_AT, envelope.occurredAt());
  }

  @Test
  void shouldRejectMalformedEventTypesAndAggregateTypes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> envelope("Connector.Completed", 1, "IMPORT_RUN", "{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> envelope("connector.completed.v1", 1, "import-run", "{}"));
  }

  @Test
  void shouldRejectNonPositiveSchemaVersionsAndBlankPayloads() {
    assertThrows(
        IllegalArgumentException.class,
        () -> envelope("connector.completed.v1", 0, "IMPORT_RUN", "{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> envelope("connector.completed.v1", 1, "IMPORT_RUN", "   "));
  }

  private static ConnectorIntegrationEventEnvelope envelope(
      String eventType, int schemaVersion, String aggregateType, String payload) {
    return new ConnectorIntegrationEventEnvelope(
        EVENT_ID,
        eventType,
        schemaVersion,
        TENANT_ID,
        aggregateType,
        AGGREGATE_ID,
        payload,
        OCCURRED_AT);
  }
}
