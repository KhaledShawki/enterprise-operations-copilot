package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Optional;

public interface BusinessPartnerRepository {

  BusinessPartner save(BusinessPartner businessPartner);

  Optional<BusinessPartner> findById(
      OperationsTenantId tenantId, BusinessPartnerId businessPartnerId);
}
