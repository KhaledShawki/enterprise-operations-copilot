package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.util.Optional;

public interface PaymentSourceMappingRepository {

  PaymentSourceMapping save(PaymentSourceMapping sourceMapping);

  Optional<PaymentSourceMapping> findBySourceIdentity(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity);
}
