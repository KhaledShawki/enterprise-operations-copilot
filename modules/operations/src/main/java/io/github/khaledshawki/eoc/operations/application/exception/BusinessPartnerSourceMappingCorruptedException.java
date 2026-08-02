package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;

public final class BusinessPartnerSourceMappingCorruptedException extends RuntimeException {

  public BusinessPartnerSourceMappingCorruptedException(
      BusinessPartnerSourceMapping sourceMapping) {
    super(
        "Business partner "
            + sourceMapping.businessPartnerId().value()
            + " referenced by source mapping was not found");
  }
}
