package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.List;
import java.util.Objects;

public record CopilotAnswerGrounding(
    String toolCallId, CopilotToolName toolName, List<CopilotEvidence> sourceEvidence) {
  public CopilotAnswerGrounding {
    Objects.requireNonNull(toolCallId, "Copilot answer tool call id cannot be null");
    toolCallId = toolCallId.strip();
    if (toolCallId.isEmpty() || toolCallId.length() > CopilotModelToolCall.MAX_CALL_ID_LENGTH) {
      throw new IllegalArgumentException("Copilot answer tool call id must be bounded");
    }
    Objects.requireNonNull(toolName, "Copilot answer tool name cannot be null");
    Objects.requireNonNull(sourceEvidence, "Copilot answer source evidence cannot be null");
    if (sourceEvidence.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Copilot answer source evidence cannot contain null values");
    }
    sourceEvidence = List.copyOf(sourceEvidence);
  }
}
