package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;

public interface BusinessPartnerSourceMappingRepository {

  BusinessPartnerSourceMapping save(BusinessPartnerSourceMapping sourceMapping);

  Optional<BusinessPartnerSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity);
}
