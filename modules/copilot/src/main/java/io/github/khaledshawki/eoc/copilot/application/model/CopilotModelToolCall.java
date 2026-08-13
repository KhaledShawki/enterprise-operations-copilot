package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.Objects;

public sealed interface CopilotModelToolCall
    permits CopilotModelToolCall.GetReceivable,
        CopilotModelToolCall.ListReceivables,
        CopilotModelToolCall.GetReceivablesSummary,
        CopilotModelToolCall.Unsupported {

  int MAX_CALL_ID_LENGTH = 128;
  int MAX_TOOL_NAME_LENGTH = 128;

  String callId();

  record GetReceivable(String callId, GetReceivableToolRequest request)
      implements CopilotModelToolCall {
    public GetReceivable {
      callId = requireCallId(callId);
      Objects.requireNonNull(request, "Copilot model get-receivable request cannot be null");
    }
  }

  record ListReceivables(String callId, ListReceivablesToolRequest request)
      implements CopilotModelToolCall {
    public ListReceivables {
      callId = requireCallId(callId);
      Objects.requireNonNull(request, "Copilot model list-receivables request cannot be null");
    }
  }

  record GetReceivablesSummary(String callId, GetReceivablesSummaryToolRequest request)
      implements CopilotModelToolCall {
    public GetReceivablesSummary {
      callId = requireCallId(callId);
      Objects.requireNonNull(request, "Copilot model summary request cannot be null");
    }
  }

  record Unsupported(String callId, String requestedToolName) implements CopilotModelToolCall {
    public Unsupported {
      callId = requireCallId(callId);
      Objects.requireNonNull(requestedToolName, "Copilot model requested tool name cannot be null");
      requestedToolName = requestedToolName.strip();
      if (requestedToolName.isEmpty() || requestedToolName.length() > MAX_TOOL_NAME_LENGTH) {
        throw new IllegalArgumentException("Copilot model requested tool name must be bounded");
      }
    }
  }

  private static String requireCallId(String callId) {
    Objects.requireNonNull(callId, "Copilot model tool call id cannot be null");
    String normalized = callId.strip();
    if (normalized.isEmpty() || normalized.length() > MAX_CALL_ID_LENGTH) {
      throw new IllegalArgumentException("Copilot model tool call id must be nonblank and bounded");
    }
    return normalized;
  }
}
