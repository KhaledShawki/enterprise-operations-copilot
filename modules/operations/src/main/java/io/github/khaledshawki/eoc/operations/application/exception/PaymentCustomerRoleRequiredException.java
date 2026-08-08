package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;

public final class PaymentCustomerRoleRequiredException extends RuntimeException {

  public PaymentCustomerRoleRequiredException(BusinessPartnerId businessPartnerId) {
    super(
        "Business partner "
            + businessPartnerId.value()
            + " must have the CUSTOMER role before payments can reference it");
  }
}
