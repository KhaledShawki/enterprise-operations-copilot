package io.github.khaledshawki.eoc.analytics.application.port.out;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivablePage;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableQueryCriteria;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSnapshot;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableReadPort {

  Optional<ReceivableSnapshot> findById(AnalyticsTenantId tenantId, UUID invoiceId);

  ReceivablePage findPage(ReceivableQueryCriteria criteria);
}
