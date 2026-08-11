package io.github.khaledshawki.eoc.operations.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingOperationsIntegrationEventTest {

  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T12:00:00Z");

  @Test
  void shouldDeriveImmutableStreamIdentityAndMaterializeAllocatedEvidence() {
    PendingOperationsIntegrationEvent pending =
        new PendingOperationsIntegrationEvent(
            OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
            TENANT_ID,
            OCCURRED_AT,
            invoicePayload());

    OperationsIntegrationEvent event = pending.materialize(EVENT_ID, 4);

    assertEquals("INVOICE", pending.aggregateType());
    assertEquals(INVOICE_ID, pending.aggregateId());
    assertEquals(EVENT_ID, event.eventId());
    assertEquals(4, event.aggregateVersion());
    assertEquals(pending.payload(), event.payload());
  }

  @Test
  void shouldRejectPayloadThatDoesNotMatchThePendingContract() {
    PaymentSynchronizedPayload payment =
        new PaymentSynchronizedPayload(
            INVOICE_ID,
            UUID.randomUUID(),
            new OperationsMoneyPayload(BigDecimal.TEN, "EUR"),
            LocalDate.of(2026, 8, 1),
            false,
            "RECORDED",
            source());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PendingOperationsIntegrationEvent(
                OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
                TENANT_ID,
                OCCURRED_AT,
                payment));
  }

  private static InvoiceSynchronizedPayload invoicePayload() {
    return new InvoiceSynchronizedPayload(
        INVOICE_ID,
        UUID.randomUUID(),
        "INV-701",
        new OperationsMoneyPayload(new BigDecimal("100.00"), "EUR"),
        new OperationsMoneyPayload(BigDecimal.ZERO, "EUR"),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        false,
        "OPEN",
        source());
  }

  private static SourceRecordEvidence source() {
    return new SourceRecordEvidence(
        UUID.randomUUID(), "SOURCE_RECORD_ID", "source-701", "v1", Optional.empty());
  }
}
