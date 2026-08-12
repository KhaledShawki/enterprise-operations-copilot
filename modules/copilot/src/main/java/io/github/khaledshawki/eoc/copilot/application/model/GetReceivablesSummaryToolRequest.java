package io.github.khaledshawki.eoc.copilot.application.model;

import io.github.khaledshawki.eoc.copilot.application.exception.InvalidCopilotToolArgumentsException;
import java.time.LocalDate;
import java.util.Optional;

public record GetReceivablesSummaryToolRequest(Optional<LocalDate> businessDate) {
  public GetReceivablesSummaryToolRequest {
    if (businessDate == null) {
      throw new InvalidCopilotToolArgumentsException("businessDate container is required");
    }
  }

  public static GetReceivablesSummaryToolRequest current() {
    return new GetReceivablesSummaryToolRequest(Optional.empty());
  }

  public CopilotToolName toolName() {
    return CopilotToolName.GET_RECEIVABLES_SUMMARY;
  }
}
