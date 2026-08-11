package io.github.khaledshawki.eoc.operations.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsIntegrationEventTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000203");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T08:00:00Z");

  @Test
  void shouldExposeVersionedContractIdentity() {
    OperationsIntegrationEvent event = event(invoicePayload(), "INVOICE", INVOICE_ID, 3);

    assertEquals("operations.invoice.synchronized.v1", event.eventType());
    assertEquals(1, event.schemaVersion());
    assertEquals(3, event.aggregateVersion());
  }

  @Test
  void shouldRejectNonPositiveAggregateVersion() {
    assertThrows(
        IllegalArgumentException.class, () -> event(invoicePayload(), "INVOICE", INVOICE_ID, 0));
  }

  @Test
  void shouldRejectAggregateTypePayloadTypeAndIdentityMismatches() {
    assertThrows(
        IllegalArgumentException.class, () -> event(invoicePayload(), "PAYMENT", INVOICE_ID, 1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            event(
                new PaymentSynchronizedPayload(
                    INVOICE_ID,
                    UUID.randomUUID(),
                    new OperationsMoneyPayload(java.math.BigDecimal.TEN, "EUR"),
                    LocalDate.of(2026, 8, 1),
                    false,
                    "RECORDED",
                    source()),
                "INVOICE",
                INVOICE_ID,
                1));
    assertThrows(
        IllegalArgumentException.class,
        () -> event(invoicePayload(), "INVOICE", UUID.randomUUID(), 1));
  }

  private static OperationsIntegrationEvent event(
      OperationsIntegrationEventPayload payload,
      String aggregateType,
      UUID aggregateId,
      long aggregateVersion) {
    return new OperationsIntegrationEvent(
        EVENT_ID,
        OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
        TENANT_ID,
        aggregateType,
        aggregateId,
        aggregateVersion,
        OCCURRED_AT,
        payload);
  }

  private static InvoiceSynchronizedPayload invoicePayload() {
    return new InvoiceSynchronizedPayload(
        INVOICE_ID,
        UUID.randomUUID(),
        "INV-200",
        new OperationsMoneyPayload(new java.math.BigDecimal("100.00"), "EUR"),
        new OperationsMoneyPayload(new java.math.BigDecimal("0.00"), "EUR"),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        false,
        "OPEN",
        source());
  }

  private static SourceRecordEvidence source() {
    return new SourceRecordEvidence(
        UUID.randomUUID(), "SOURCE_RECORD_ID", "invoice-200", "v1", Optional.empty());
  }
}
