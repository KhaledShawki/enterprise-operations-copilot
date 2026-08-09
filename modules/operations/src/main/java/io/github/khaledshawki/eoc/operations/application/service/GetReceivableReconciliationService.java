package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableReconciliationStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationEvidence;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableReconciliationResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableReconciliationEvidenceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GetReceivableReconciliationService
    implements GetReceivableReconciliationUseCase {

  private final InvoiceRepository invoiceRepository;
  private final ReceivableReconciliationEvidenceRepository evidenceRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public GetReceivableReconciliationService(
      InvoiceRepository invoiceRepository,
      ReceivableReconciliationEvidenceRepository evidenceRepository,
      OperationsAuthorizationPort authorizationPort) {
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice repository cannot be null");
    this.evidenceRepository =
        Objects.requireNonNull(
            evidenceRepository, "Receivable reconciliation evidence repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public ReceivableReconciliationResult get(GetReceivableReconciliationQuery query) {
    Objects.requireNonNull(query, "Receivable reconciliation query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    authorize(query, tenantId);
    InvoiceId invoiceId = InvoiceId.of(query.invoiceId());

    Optional<Invoice> invoiceLookup = invoiceRepository.findById(tenantId, invoiceId);
    if (invoiceLookup == null) {
      throw corrupted("Invoice repository returned a null lookup result");
    }
    Invoice invoice =
        invoiceLookup.orElseThrow(() -> new InvoiceNotFoundException(tenantId, invoiceId));

    ReceivableReconciliationEvidence evidence =
        evidenceRepository.load(
            tenantId, invoice.id(), invoice.customerId(), invoice.originalAmount().currency());
    if (evidence == null) {
      throw corrupted("Receivable reconciliation evidence repository returned null");
    }

    EnumSet<ReceivableReconciliationIssue> issues =
        evidence.issues().isEmpty()
            ? EnumSet.noneOf(ReceivableReconciliationIssue.class)
            : EnumSet.copyOf(evidence.issues());

    Optional<Money> localAllocatedAmount = evidence.localAllocatedAmount();
    if (invoice.cancelled() && evidence.activeAllocationCount() > 0) {
      issues.add(ReceivableReconciliationIssue.INVOICE_CANCELLED_WITH_ACTIVE_ALLOCATIONS);
    }
    localAllocatedAmount.ifPresent(
        local -> {
          if (local.compareTo(invoice.originalAmount()) > 0) {
            issues.add(ReceivableReconciliationIssue.INVOICE_ALLOCATION_CAPACITY_EXCEEDED);
          }
        });

    ReceivableReconciliationStatus status;
    Optional<Money> difference;
    if (!issues.isEmpty()) {
      status = ReceivableReconciliationStatus.CONFLICT;
      difference = Optional.empty();
    } else {
      Money local =
          localAllocatedAmount.orElseThrow(
              () -> corrupted("Comparable reconciliation evidence omitted local amount"));
      Money signedDifference;
      try {
        signedDifference = invoice.paidAmount().subtract(local);
      } catch (RuntimeException exception) {
        throw corrupted("Receivable reconciliation amounts cannot be compared", exception);
      }
      difference = Optional.of(signedDifference);
      status =
          signedDifference.isZero()
              ? ReceivableReconciliationStatus.MATCHED
              : signedDifference.isPositive()
                  ? ReceivableReconciliationStatus.SOURCE_AHEAD
                  : ReceivableReconciliationStatus.LOCAL_AHEAD;
    }

    try {
      return new ReceivableReconciliationResult(
          invoice.id(),
          invoice.tenantId(),
          invoice.customerId(),
          invoice.invoiceNumber(),
          invoice.originalAmount(),
          invoice.paidAmount(),
          localAllocatedAmount,
          difference,
          invoice.status(),
          invoice.cancelled(),
          evidence.activeAllocationCount(),
          status,
          Set.copyOf(issues));
    } catch (IllegalArgumentException exception) {
      throw corrupted("Receivable reconciliation read model is internally inconsistent", exception);
    }
  }

  private void authorize(GetReceivableReconciliationQuery query, OperationsTenantId tenantId) {
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_RECEIVABLE_RECONCILIATIONS)) {
      throw new OperationsAccessDeniedException(
          tenantId, OperationsPermission.READ_RECEIVABLE_RECONCILIATIONS);
    }
  }

  private static ReceivableReconciliationStateCorruptedException corrupted(String detail) {
    return new ReceivableReconciliationStateCorruptedException(detail);
  }

  private static ReceivableReconciliationStateCorruptedException corrupted(
      String detail, RuntimeException cause) {
    return new ReceivableReconciliationStateCorruptedException(detail, cause);
  }
}
