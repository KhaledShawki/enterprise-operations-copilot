package io.github.khaledshawki.eoc.copilot.application.port.out;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableListCriteria;
import java.time.LocalDate;
import java.util.UUID;

public interface CopilotReceivablesDataPort {
  CopilotReceivable getReceivable(UUID tenantId, UUID invoiceId, LocalDate businessDate);

  CopilotReceivablePage listReceivables(UUID tenantId, ReceivableListCriteria criteria);

  CopilotReceivablesSummary getReceivablesSummary(UUID tenantId, LocalDate businessDate);
}
