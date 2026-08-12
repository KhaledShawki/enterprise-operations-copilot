package io.github.khaledshawki.eoc.copilot.application.port.in;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;

public interface ExecuteCopilotToolUseCase {
  CopilotReceivable execute(CopilotExecutionContext context, GetReceivableToolRequest request);

  CopilotReceivablePage execute(
      CopilotExecutionContext context, ListReceivablesToolRequest request);

  CopilotReceivablesSummary execute(
      CopilotExecutionContext context, GetReceivablesSummaryToolRequest request);
}
