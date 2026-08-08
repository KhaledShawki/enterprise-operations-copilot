package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;

public final class PaymentSourceMappingCorruptedException extends RuntimeException {

  public PaymentSourceMappingCorruptedException(PaymentSourceMapping sourceMapping) {
    super(
        "Payment "
            + sourceMapping.paymentId().value()
            + " referenced by source mapping was not found");
  }
}
