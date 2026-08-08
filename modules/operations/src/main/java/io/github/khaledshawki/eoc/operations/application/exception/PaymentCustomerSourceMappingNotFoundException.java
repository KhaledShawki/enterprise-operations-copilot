package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;

public final class PaymentCustomerSourceMappingNotFoundException extends RuntimeException {

  public PaymentCustomerSourceMappingNotFoundException(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity customerSourceIdentity) {
    super(
        "Customer source mapping was not found for tenant "
            + tenantId.value()
            + ", source system "
            + sourceSystemId.value()
            + ", and identity "
            + customerSourceIdentity.kind()
            + ":"
            + customerSourceIdentity.value());
  }
}
