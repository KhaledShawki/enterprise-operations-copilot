package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;

public final class InvoiceSourceMappingCorruptedException extends RuntimeException {

  public InvoiceSourceMappingCorruptedException(InvoiceSourceMapping sourceMapping) {
    super(
        "Invoice "
            + sourceMapping.invoiceId().value()
            + " referenced by source mapping was not found");
  }
}
