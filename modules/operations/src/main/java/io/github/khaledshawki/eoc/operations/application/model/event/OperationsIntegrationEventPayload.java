package io.github.khaledshawki.eoc.operations.application.model.event;

import java.util.UUID;

public sealed interface OperationsIntegrationEventPayload
    permits BusinessPartnerSynchronizedPayload,
        InvoiceSynchronizedPayload,
        PaymentSynchronizedPayload,
        ReceivableAllocationAppliedPayload,
        ReceivableAllocationReversedPayload {

  UUID aggregateId();
}
