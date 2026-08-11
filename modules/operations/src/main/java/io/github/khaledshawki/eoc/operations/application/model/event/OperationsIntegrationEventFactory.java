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
    return pendingBusinessPartnerSynchronized(businessPartner, source, occurredAt)
        .materialize(eventId, aggregateVersion);
  }

  public static PendingOperationsIntegrationEvent pendingBusinessPartnerSynchronized(
      BusinessPartner businessPartner, SourceRecordEvidence source, Instant occurredAt) {
    Objects.requireNonNull(businessPartner, "Business partner cannot be null");
    return pendingEvent(
        OperationsIntegrationEventType.BUSINESS_PARTNER_SYNCHRONIZED,
        businessPartner.tenantId(),
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
    return pendingInvoiceSynchronized(invoice, source, occurredAt)
        .materialize(eventId, aggregateVersion);
  }

  public static PendingOperationsIntegrationEvent pendingInvoiceSynchronized(
      Invoice invoice, SourceRecordEvidence source, Instant occurredAt) {
    Objects.requireNonNull(invoice, "Invoice cannot be null");
    return pendingEvent(
        OperationsIntegrationEventType.INVOICE_SYNCHRONIZED,
        invoice.tenantId(),
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
    return pendingPaymentSynchronized(payment, source, occurredAt)
        .materialize(eventId, aggregateVersion);
  }

  public static PendingOperationsIntegrationEvent pendingPaymentSynchronized(
      Payment payment, SourceRecordEvidence source, Instant occurredAt) {
    Objects.requireNonNull(payment, "Payment cannot be null");
    return pendingEvent(
        OperationsIntegrationEventType.PAYMENT_SYNCHRONIZED,
        payment.tenantId(),
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
    return pendingReceivableAllocationApplied(tenantId, allocation, occurredAt)
        .materialize(eventId, aggregateVersion);
  }

  public static PendingOperationsIntegrationEvent pendingReceivableAllocationApplied(
      OperationsTenantId tenantId, ReceivableAllocationResult allocation, Instant occurredAt) {
    requireState(allocation, ReceivableAllocationState.ACTIVE);
    return pendingEvent(
        OperationsIntegrationEventType.RECEIVABLE_ALLOCATION_APPLIED,
        tenantId,
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
    return pendingReceivableAllocationReversed(tenantId, allocation, occurredAt)
        .materialize(eventId, aggregateVersion);
  }

  public static PendingOperationsIntegrationEvent pendingReceivableAllocationReversed(
      OperationsTenantId tenantId, ReceivableAllocationResult allocation, Instant occurredAt) {
    requireState(allocation, ReceivableAllocationState.REVERSED);
    return pendingEvent(
        OperationsIntegrationEventType.RECEIVABLE_ALLOCATION_REVERSED,
        tenantId,
        occurredAt,
        new ReceivableAllocationReversedPayload(
            allocation.settlementId().value(),
            allocation.paymentId().value(),
            allocation.allocationId().value(),
            allocation.invoiceId().value(),
            OperationsMoneyPayload.from(allocation.amount())));
  }

  private static PendingOperationsIntegrationEvent pendingEvent(
      OperationsIntegrationEventType type,
      OperationsTenantId tenantId,
      Instant occurredAt,
      OperationsIntegrationEventPayload payload) {
    Objects.requireNonNull(tenantId, "Operations tenant id cannot be null");
    return new PendingOperationsIntegrationEvent(type, tenantId.value(), occurredAt, payload);
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
