package io.github.khaledshawki.eoc.operations.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsIntegrationEventEnvelopeTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T08:00:00Z");

  @Test
  void shouldAcceptCompleteBrokerNeutralEnvelope() {
    OperationsIntegrationEventEnvelope envelope = envelope(1, "INVOICE", 7, "{}");

    assertEquals(EVENT_ID, envelope.eventId());
    assertEquals("operations.invoice.synchronized.v1", envelope.eventType());
    assertEquals(1, envelope.schemaVersion());
    assertEquals(TENANT_ID, envelope.tenantId());
    assertEquals("INVOICE", envelope.aggregateType());
    assertEquals(AGGREGATE_ID, envelope.aggregateId());
    assertEquals(7, envelope.aggregateVersion());
    assertEquals("{}", envelope.payload());
    assertEquals(OCCURRED_AT, envelope.occurredAt());
  }

  @Test
  void shouldRejectInvalidEventContractIdentity() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OperationsIntegrationEventEnvelope(
                EVENT_ID,
                "Operations.Invoice.Changed.V1",
                1,
                TENANT_ID,
                "INVOICE",
                AGGREGATE_ID,
                1,
                "{}",
                OCCURRED_AT));
    assertThrows(IllegalArgumentException.class, () -> envelope(0, "INVOICE", 1, "{}"));
  }

  @Test
  void shouldRejectInvalidAggregateIdentityOrVersion() {
    assertThrows(IllegalArgumentException.class, () -> envelope(1, "invoice", 1, "{}"));
    assertThrows(IllegalArgumentException.class, () -> envelope(1, "INVOICE", 0, "{}"));
    assertThrows(IllegalArgumentException.class, () -> envelope(1, "INVOICE", -1, "{}"));
  }

  @Test
  void shouldRejectMissingOrBlankPayload() {
    assertThrows(NullPointerException.class, () -> envelope(1, "INVOICE", 1, null));
    assertThrows(IllegalArgumentException.class, () -> envelope(1, "INVOICE", 1, "  \n"));
  }

  @Test
  void shouldRejectMissingImmutableEnvelopeEvidence() {
    assertThrows(
        NullPointerException.class,
        () ->
            new OperationsIntegrationEventEnvelope(
                null,
                "operations.invoice.synchronized.v1",
                1,
                TENANT_ID,
                "INVOICE",
                AGGREGATE_ID,
                1,
                "{}",
                OCCURRED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new OperationsIntegrationEventEnvelope(
                EVENT_ID,
                "operations.invoice.synchronized.v1",
                1,
                null,
                "INVOICE",
                AGGREGATE_ID,
                1,
                "{}",
                OCCURRED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new OperationsIntegrationEventEnvelope(
                EVENT_ID,
                "operations.invoice.synchronized.v1",
                1,
                TENANT_ID,
                "INVOICE",
                null,
                1,
                "{}",
                OCCURRED_AT));
    assertThrows(
        NullPointerException.class,
        () ->
            new OperationsIntegrationEventEnvelope(
                EVENT_ID,
                "operations.invoice.synchronized.v1",
                1,
                TENANT_ID,
                "INVOICE",
                AGGREGATE_ID,
                1,
                "{}",
                null));
  }

  private static OperationsIntegrationEventEnvelope envelope(
      int schemaVersion, String aggregateType, long aggregateVersion, String payload) {
    return new OperationsIntegrationEventEnvelope(
        EVENT_ID,
        "operations.invoice.synchronized.v1",
        schemaVersion,
        TENANT_ID,
        aggregateType,
        AGGREGATE_ID,
        aggregateVersion,
        payload,
        OCCURRED_AT);
  }
}
