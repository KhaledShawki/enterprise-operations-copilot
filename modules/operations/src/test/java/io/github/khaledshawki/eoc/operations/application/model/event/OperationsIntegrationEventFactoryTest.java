package io.github.khaledshawki.eoc.operations.application.model.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsIntegrationEventFactoryTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000301"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000302"));
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-11T08:00:00Z");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 1);

  @Test
  void shouldCreateBusinessPartnerSnapshotWithoutEmailAddress() {
    BusinessPartner businessPartner =
        BusinessPartner.reconstitute(
            CUSTOMER_ID,
            TENANT_ID,
            new BusinessPartnerProfile(
                "BP-300", "Example Customer", Optional.of("private@example.com")),
            Set.of(BusinessPartnerRole.VENDOR, BusinessPartnerRole.CUSTOMER));

    OperationsIntegrationEvent event =
        OperationsIntegrationEventFactory.businessPartnerSynchronized(
            UUID.randomUUID(), 4, businessPartner, source(), OCCURRED_AT);

    BusinessPartnerSynchronizedPayload payload =
        assertInstanceOf(BusinessPartnerSynchronizedPayload.class, event.payload());
    assertEquals("operations.business-partner.synchronized.v1", event.eventType());
    assertEquals("BUSINESS_PARTNER", event.aggregateType());
    assertEquals(CUSTOMER_ID.value(), event.aggregateId());
    assertEquals(java.util.List.of("CUSTOMER", "VENDOR"), payload.roles());
    assertEquals("Example Customer", payload.displayName());
  }

  @Test
  void shouldCreateInvoiceSnapshotFromCanonicalFacts() {
    Invoice invoice = invoice();

    OperationsIntegrationEvent event =
        OperationsIntegrationEventFactory.invoiceSynchronized(
            UUID.randomUUID(), 2, invoice, source(), OCCURRED_AT);

    InvoiceSynchronizedPayload payload =
        assertInstanceOf(InvoiceSynchronizedPayload.class, event.payload());
    assertEquals("operations.invoice.synchronized.v1", event.eventType());
    assertEquals("PARTIALLY_PAID", payload.status());
    assertEquals(new java.math.BigDecimal("25.00"), payload.paidAmount().amount());
    assertEquals(invoice.id().value(), event.aggregateId());
  }

  @Test
  void shouldCreatePaymentSnapshotFromCanonicalFacts() {
    Payment payment =
        Payment.reconstitute(
            PaymentId.of(UUID.randomUUID()),
            TENANT_ID,
            CUSTOMER_ID,
            Money.of("50.00", EUR),
            BUSINESS_DATE,
            true);

    OperationsIntegrationEvent event =
        OperationsIntegrationEventFactory.paymentSynchronized(
            UUID.randomUUID(), 8, payment, source(), OCCURRED_AT);

    PaymentSynchronizedPayload payload =
        assertInstanceOf(PaymentSynchronizedPayload.class, event.payload());
    assertEquals("operations.payment.synchronized.v1", event.eventType());
    assertEquals("REVERSED", payload.status());
    assertEquals(8, event.aggregateVersion());
  }

  @Test
  void shouldCreateAppliedAndReversedAllocationTransitions() {
    ReceivableAllocationResult active = allocation(ReceivableAllocationState.ACTIVE);
    ReceivableAllocationResult reversed = allocation(ReceivableAllocationState.REVERSED);

    OperationsIntegrationEvent applied =
        OperationsIntegrationEventFactory.receivableAllocationApplied(
            UUID.randomUUID(), 1, TENANT_ID, active, OCCURRED_AT);
    OperationsIntegrationEvent reversedEvent =
        OperationsIntegrationEventFactory.receivableAllocationReversed(
            UUID.randomUUID(), 2, TENANT_ID, reversed, OCCURRED_AT.plusSeconds(1));

    assertEquals("operations.receivable-allocation.applied.v1", applied.eventType());
    assertEquals("operations.receivable-allocation.reversed.v1", reversedEvent.eventType());
    assertEquals("RECEIVABLE_SETTLEMENT", applied.aggregateType());
    assertEquals(active.settlementId().value(), applied.aggregateId());
    assertInstanceOf(ReceivableAllocationAppliedPayload.class, applied.payload());
    assertInstanceOf(ReceivableAllocationReversedPayload.class, reversedEvent.payload());
  }

  @Test
  void shouldRejectAllocationStateThatDoesNotMatchTransition() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OperationsIntegrationEventFactory.receivableAllocationApplied(
                UUID.randomUUID(),
                1,
                TENANT_ID,
                allocation(ReceivableAllocationState.REVERSED),
                OCCURRED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            OperationsIntegrationEventFactory.receivableAllocationReversed(
                UUID.randomUUID(),
                1,
                TENANT_ID,
                allocation(ReceivableAllocationState.ACTIVE),
                OCCURRED_AT));
  }

  private static Invoice invoice() {
    return Invoice.reconstitute(
        InvoiceId.of(UUID.randomUUID()),
        TENANT_ID,
        CUSTOMER_ID,
        new InvoiceNumber("INV-300"),
        Money.of("100.00", EUR),
        Money.of("25.00", EUR),
        BUSINESS_DATE,
        BUSINESS_DATE.plusDays(30),
        false);
  }

  private static ReceivableAllocationResult allocation(ReceivableAllocationState state) {
    return new ReceivableAllocationResult(
        ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000310")),
        PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000311")),
        ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000312")),
        InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000313")),
        Money.of("10.00", EUR),
        state);
  }

  private static SourceRecordEvidence source() {
    return SourceRecordEvidence.from(
        SourceSystemId.of(UUID.fromString("00000000-0000-0000-0000-000000000320")),
        SourceRecordIdentity.sourceRecordId("source-300"),
        new SourceRecordVersion("v3"),
        Optional.of(Instant.parse("2026-08-11T07:00:00Z")));
  }
}
