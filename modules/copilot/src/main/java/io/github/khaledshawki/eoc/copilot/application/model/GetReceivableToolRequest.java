package io.github.khaledshawki.eoc.copilot.application.model;

import io.github.khaledshawki.eoc.copilot.application.exception.InvalidCopilotToolArgumentsException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public record GetReceivableToolRequest(UUID invoiceId, Optional<LocalDate> businessDate) {
  public GetReceivableToolRequest {
    if (invoiceId == null) {
      throw new InvalidCopilotToolArgumentsException("invoiceId is required");
    }
    if (businessDate == null) {
      throw new InvalidCopilotToolArgumentsException("businessDate container is required");
    }
  }

  public static GetReceivableToolRequest current(UUID invoiceId) {
    return new GetReceivableToolRequest(invoiceId, Optional.empty());
  }

  public CopilotToolName toolName() {
    return CopilotToolName.GET_RECEIVABLE;
  }
}
