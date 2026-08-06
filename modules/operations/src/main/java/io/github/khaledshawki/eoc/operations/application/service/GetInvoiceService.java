package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;

public final class GetInvoiceService implements GetInvoiceUseCase {

  private final InvoiceRepository invoiceRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public GetInvoiceService(
      InvoiceRepository invoiceRepository, OperationsAuthorizationPort authorizationPort) {
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public InvoiceResult get(GetInvoiceQuery query) {
    Objects.requireNonNull(query, "Invoice query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    authorize(query, tenantId);
    InvoiceId invoiceId = InvoiceId.of(query.invoiceId());
    return invoiceRepository
        .findById(tenantId, invoiceId)
        .map(invoice -> InvoiceResult.from(invoice, query.businessDate()))
        .orElseThrow(() -> new InvoiceNotFoundException(tenantId, invoiceId));
  }

  private void authorize(GetInvoiceQuery query, OperationsTenantId tenantId) {
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_INVOICES)) {
      throw new OperationsAccessDeniedException(tenantId, OperationsPermission.READ_INVOICES);
    }
  }
}
