package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;

public final class InvoiceCustomerRoleRequiredException extends RuntimeException {

  public InvoiceCustomerRoleRequiredException(BusinessPartnerId businessPartnerId) {
    super(
        "Business partner "
            + businessPartnerId.value()
            + " must have the CUSTOMER role before invoices can reference it");
  }
}
