package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableReconciliationResult;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReceivableReconciliationResponse(
    UUID invoiceId,
    UUID tenantId,
    UUID customerId,
    String invoiceNumber,
    MoneyResponse originalAmount,
    MoneyResponse sourcePaidAmount,
    MoneyResponse localAllocatedAmount,
    MoneyResponse difference,
    InvoiceStatus sourceStatus,
    boolean cancelled,
    long activeAllocationCount,
    ReceivableReconciliationStatus status,
    List<ReceivableReconciliationIssue> issues) {

  public ReceivableReconciliationResponse {
    Objects.requireNonNull(
        invoiceId, "Receivable reconciliation response Invoice id cannot be null");
    Objects.requireNonNull(tenantId, "Receivable reconciliation response tenant id cannot be null");
    Objects.requireNonNull(
        customerId, "Receivable reconciliation response customer id cannot be null");
    Objects.requireNonNull(
        invoiceNumber, "Receivable reconciliation response Invoice number cannot be null");
    Objects.requireNonNull(
        originalAmount, "Receivable reconciliation response original amount cannot be null");
    Objects.requireNonNull(
        sourcePaidAmount, "Receivable reconciliation response source paid amount cannot be null");
    Objects.requireNonNull(
        sourceStatus, "Receivable reconciliation response source status cannot be null");
    Objects.requireNonNull(status, "Receivable reconciliation response status cannot be null");
    Objects.requireNonNull(issues, "Receivable reconciliation response issues cannot be null");
    issues = List.copyOf(issues);
  }

  static ReceivableReconciliationResponse from(ReceivableReconciliationResult result) {
    Objects.requireNonNull(result, "Receivable reconciliation result cannot be null");
    return new ReceivableReconciliationResponse(
        result.invoiceId().value(),
        result.tenantId().value(),
        result.customerId().value(),
        result.invoiceNumber().value(),
        MoneyResponse.from(result.originalAmount()),
        MoneyResponse.from(result.sourcePaidAmount()),
        result.localAllocatedAmount().map(MoneyResponse::from).orElse(null),
        result.difference().map(MoneyResponse::from).orElse(null),
        result.sourceStatus(),
        result.cancelled(),
        result.activeAllocationCount(),
        result.status(),
        result.issues().stream().sorted().toList());
  }
}
