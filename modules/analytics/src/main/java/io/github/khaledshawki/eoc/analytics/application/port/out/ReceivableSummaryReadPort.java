package io.github.khaledshawki.eoc.analytics.application.port.out;

import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSummarySnapshot;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.time.LocalDate;

public interface ReceivableSummaryReadPort {

  ReceivableSummarySnapshot summarize(AnalyticsTenantId tenantId, LocalDate businessDate);
}
