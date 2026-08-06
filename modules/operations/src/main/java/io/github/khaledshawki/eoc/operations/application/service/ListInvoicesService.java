package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoicePageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceQueryRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;

public final class ListInvoicesService implements ListInvoicesUseCase {

  private final InvoiceQueryRepository invoiceQueryRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public ListInvoicesService(
      InvoiceQueryRepository invoiceQueryRepository,
      OperationsAuthorizationPort authorizationPort) {
    this.invoiceQueryRepository =
        Objects.requireNonNull(invoiceQueryRepository, "Invoice query repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public InvoicePageResult list(ListInvoicesQuery query) {
    Objects.requireNonNull(query, "Invoice list query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_INVOICES)) {
      throw new OperationsAccessDeniedException(tenantId, OperationsPermission.READ_INVOICES);
    }
    return InvoicePageResult.from(
        invoiceQueryRepository.findPage(query.criteria()), query.businessDate());
  }
}
