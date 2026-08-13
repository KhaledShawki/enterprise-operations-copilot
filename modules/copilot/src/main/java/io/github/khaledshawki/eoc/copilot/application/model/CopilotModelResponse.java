package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public sealed interface CopilotModelResponse
    permits CopilotModelResponse.ToolCalls, CopilotModelResponse.Answer {

  record ToolCalls(List<CopilotModelToolCall> toolCalls) implements CopilotModelResponse {
    public static final int MAX_TOOL_CALLS = 3;

    public ToolCalls {
      Objects.requireNonNull(toolCalls, "Copilot model tool calls cannot be null");
      if (toolCalls.isEmpty()
          || toolCalls.size() > MAX_TOOL_CALLS
          || toolCalls.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException(
            "Copilot model response must contain bounded tool calls");
      }
      toolCalls = List.copyOf(toolCalls);
    }
  }

  record Answer(List<String> groundingToolCallIds) implements CopilotModelResponse {
    public static final int MAX_GROUNDING_TOOL_CALLS = 3;

    public Answer {
      Objects.requireNonNull(
          groundingToolCallIds, "Copilot model answer grounding ids cannot be null");
      if (groundingToolCallIds.isEmpty()
          || groundingToolCallIds.size() > MAX_GROUNDING_TOOL_CALLS
          || groundingToolCallIds.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException(
            "Copilot model answer must cite a bounded set of executed tool results");
      }
      groundingToolCallIds =
          groundingToolCallIds.stream()
              .map(String::strip)
              .peek(
                  value -> {
                    if (value.isEmpty()
                        || value.length() > CopilotModelToolCall.MAX_CALL_ID_LENGTH) {
                      throw new IllegalArgumentException(
                          "Copilot model answer grounding id must be bounded");
                    }
                  })
              .toList();
      if (new HashSet<>(groundingToolCallIds).size() != groundingToolCallIds.size()) {
        throw new IllegalArgumentException("Copilot model answer grounding ids must be unique");
      }
    }
  }
}
