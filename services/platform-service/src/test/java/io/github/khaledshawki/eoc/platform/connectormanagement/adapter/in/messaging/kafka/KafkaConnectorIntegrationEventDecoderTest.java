package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KafkaConnectorIntegrationEventDecoderTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");

  private final KafkaConnectorIntegrationEventDecoder decoder =
      new KafkaConnectorIntegrationEventDecoder(JsonMapper.builder().build(), 16_384);

  @Test
  void decodesTheCompleteBrokerNeutralEnvelope() {
    ConnectorIntegrationEventEnvelope event = decoder.decode(validEnvelope());

    assertEquals(EVENT_ID, event.eventId());
    assertEquals("connector.import-run.completed.v1", event.eventType());
    assertEquals(1, event.schemaVersion());
    assertEquals(TENANT_ID, event.tenantId());
    assertEquals("IMPORT_RUN", event.aggregateType());
    assertEquals(AGGREGATE_ID, event.aggregateId());
    assertEquals(Instant.parse("2026-08-09T19:00:00Z"), event.occurredAt());
    assertEquals("{\"acceptedCount\":2}", event.payload());
  }

  @Test
  void rejectsUnknownEnvelopeFields() {
    String value =
        validEnvelope().replace("\"occurredAt\"", "\"claimOwner\":\"worker\",\"occurredAt\"");

    TerminalConnectorKafkaConsumptionException exception =
        assertThrows(TerminalConnectorKafkaConsumptionException.class, () -> decoder.decode(value));

    assertEquals(KafkaConnectorIntegrationEventDecoder.INVALID_ENVELOPE, exception.failureCode());
    assertFalse(exception.retryable());
  }

  @Test
  void rejectsNonObjectPayloadsAndMalformedStructuralFields() {
    String arrayPayload = validEnvelope().replace("{\"acceptedCount\":2}", "[1,2]");
    String stringVersion =
        validEnvelope().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"");

    assertInvalid(arrayPayload);
    assertInvalid(stringVersion);
    assertInvalid(validEnvelope().replace(EVENT_ID.toString(), "not-a-uuid"));
    assertInvalid("not-json");
    assertInvalid(null);
  }

  @Test
  void rejectsRecordsAboveTheConfiguredUtf8ByteLimit() {
    KafkaConnectorIntegrationEventDecoder sizeLimited =
        new KafkaConnectorIntegrationEventDecoder(JsonMapper.builder().build(), 10);

    TerminalConnectorKafkaConsumptionException exception =
        assertThrows(
            TerminalConnectorKafkaConsumptionException.class,
            () -> sizeLimited.decode(validEnvelope()));

    assertEquals(KafkaConnectorIntegrationEventDecoder.EVENT_TOO_LARGE, exception.failureCode());
  }

  private void assertInvalid(String value) {
    TerminalConnectorKafkaConsumptionException exception =
        assertThrows(TerminalConnectorKafkaConsumptionException.class, () -> decoder.decode(value));
    assertEquals(KafkaConnectorIntegrationEventDecoder.INVALID_ENVELOPE, exception.failureCode());
  }

  private static String validEnvelope() {
    return """
    {
      "eventId":"00000000-0000-0000-0000-000000000081",
      "eventType":"connector.import-run.completed.v1",
      "schemaVersion":1,
      "tenantId":"00000000-0000-0000-0000-000000000010",
      "aggregateType":"IMPORT_RUN",
      "aggregateId":"00000000-0000-0000-0000-000000000082",
      "payload":{"acceptedCount":2},
      "occurredAt":"2026-08-09T19:00:00Z"
    }
    """;
  }
}
