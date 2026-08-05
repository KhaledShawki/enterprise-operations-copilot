package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;

public interface InvoiceSourceMappingRepository {

  InvoiceSourceMapping save(InvoiceSourceMapping sourceMapping);

  Optional<InvoiceSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity);
}
