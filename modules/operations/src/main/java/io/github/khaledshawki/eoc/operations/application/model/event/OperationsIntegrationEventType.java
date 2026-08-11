package io.github.khaledshawki.eoc.operations.application.model.event;

public enum OperationsIntegrationEventType {
  BUSINESS_PARTNER_SYNCHRONIZED(
      "operations.business-partner.synchronized.v1",
      1,
      "BUSINESS_PARTNER",
      BusinessPartnerSynchronizedPayload.class),
  INVOICE_SYNCHRONIZED(
      "operations.invoice.synchronized.v1", 1, "INVOICE", InvoiceSynchronizedPayload.class),
  PAYMENT_SYNCHRONIZED(
      "operations.payment.synchronized.v1", 1, "PAYMENT", PaymentSynchronizedPayload.class),
  RECEIVABLE_ALLOCATION_APPLIED(
      "operations.receivable-allocation.applied.v1",
      1,
      "RECEIVABLE_SETTLEMENT",
      ReceivableAllocationAppliedPayload.class),
  RECEIVABLE_ALLOCATION_REVERSED(
      "operations.receivable-allocation.reversed.v1",
      1,
      "RECEIVABLE_SETTLEMENT",
      ReceivableAllocationReversedPayload.class);

  private final String eventType;
  private final int schemaVersion;
  private final String aggregateType;
  private final Class<? extends OperationsIntegrationEventPayload> payloadType;

  OperationsIntegrationEventType(
      String eventType,
      int schemaVersion,
      String aggregateType,
      Class<? extends OperationsIntegrationEventPayload> payloadType) {
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

  boolean supports(OperationsIntegrationEventPayload payload) {
    return payloadType.isInstance(payload);
  }
}
