package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class OperationsIntegrationEventFactory {

  private OperationsIntegrationEventFactory() {}

  public static OperationsIntegrationEvent businessPartnerSynchronized(
      UUID eventId,
      long aggregateVersion,
      BusinessPartner businessPartner,
      SourceRecordEvidence source,
      Instant occurredAt) {
    Objects.requireNonNull(businessPartner, "Business partner cannot be null");
    return event(
        eventId,
        OperationsIntegrationEventType.BUSINESS_PARTNER_SYNCHRONIZED,
        businessPartner.tenantId(),
        aggregateVersion,
        occurredAt,
        new BusinessPartnerSynchronizedPayload(
            businessPartner.id().value(),
            businessPartner.profile().partnerNumber(),
            businessPartner.profile().displayName(),
            businessPartner.roles().stream().map(Enum::name).sorted().toList(),
            source));
  }

  public static OperationsIntegrationEvent invoiceSynchronized(
      UUID eventId,
      long aggregateVersion,
      Invoice invoice,
      SourceRecordEvidence source,
      Instant occurredAt) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    return event(
        eventId,
        OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
        invoice.tenantId(),
        aggregateVersion,
        occurredAt,
        new InvoiceSynchronizedPayload(
            invoice.id().value(),
            invoice.customerId().value(),
            invoice.invoiceNumber().value(),
            OperationsMoneyPayload.from(invoice.originalAmount()),
            OperationsMoneyPayload.from(invoice.paidAmount()),
            invoice.issueDate(),
            invoice.dueDate(),
            invoice.cancelled(),
            invoice.status().name(),
            source));
  }

  public static OperationsIntegrationEvent paymentSynchronized(
      UUID eventId,
      long aggregateVersion,
      Payment payment,
      SourceRecordEvidence source,
      Instant occurredAt) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    return event(
        eventId,
        OperationsIntegrationEventType.PAYMENT_SYNCHRONIZED,
        payment.tenantId(),
        aggregateVersion,
        occurredAt,
        new PaymentSynchronizedPayload(
            payment.id().value(),
            payment.customerId().value(),
            OperationsMoneyPayload.from(payment.amount()),
            payment.paymentDate(),
            payment.reversed(),
            payment.status().name(),
            source));
  }

  public static OperationsIntegrationEvent receivableAllocationApplied(
      UUID eventId,
      long aggregateVersion,
      OperationsTenantId tenantId,
      ReceivableAllocationResult allocation,
      Instant occurredAt) {
    requireState(allocation, ReceivableAllocationState.ACTIVE);
    return event(
        eventId,
        OperationsIntegrationEventType.RECEIVABLE_ALLOCATION_APPLIED,
        tenantId,
        aggregateVersion,
        occurredAt,
        new ReceivableAllocationAppliedPayload(
            allocation.settlementId().value(),
            allocation.paymentId().value(),
            allocation.allocationId().value(),
            allocation.invoiceId().value(),
            OperationsMoneyPayload.from(allocation.amount())));
  }

  public static OperationsIntegrationEvent receivableAllocationReversed(
      UUID eventId,
      long aggregateVersion,
      OperationsTenantId tenantId,
      ReceivableAllocationResult allocation,
      Instant occurredAt) {
    requireState(allocation, ReceivableAllocationState.REVERSED);
    return event(
        eventId,
        OperationsIntegrationEventType.RECEIVABLE_ALLOCATION_REVERSED,
        tenantId,
        aggregateVersion,
        occurredAt,
        new ReceivableAllocationReversedPayload(
            allocation.settlementId().value(),
            allocation.paymentId().value(),
            allocation.allocationId().value(),
            allocation.invoiceId().value(),
            OperationsMoneyPayload.from(allocation.amount())));
  }

  private static OperationsIntegrationEvent event(
      UUID eventId,
      OperationsIntegrationEventType type,
      OperationsTenantId tenantId,
      long aggregateVersion,
      Instant occurredAt,
      OperationsIntegrationEventPayload payload) {
    Objects.requireNonNull(tenantId, "Operations tenant id cannot be null");
    return new OperationsIntegrationEvent(
        eventId,
        type,
        tenantId.value(),
        type.aggregateType(),
        payload.aggregateId(),
        aggregateVersion,
        occurredAt,
        payload);
  }

  private static void requireState(
      ReceivableAllocationResult allocation, ReceivableAllocationState expectedState) {
    Objects.requireNonNull(allocation, "Receivable allocation cannot be null");
    if (allocation.state() != expectedState) {
      throw new IllegalArgumentException(
          "Receivable allocation state does not match the Operations event contract");
    }
  }
}
