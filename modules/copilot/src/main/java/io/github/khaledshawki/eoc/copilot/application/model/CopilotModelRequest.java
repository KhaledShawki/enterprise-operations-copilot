package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.List;
import java.util.Objects;

public record CopilotModelRequest(CopilotQuestion question, List<CopilotModelTurn> completedTurns) {
  public CopilotModelRequest {
    Objects.requireNonNull(question, "Copilot model question cannot be null");
    Objects.requireNonNull(completedTurns, "Copilot model completed turns cannot be null");
    if (completedTurns.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Copilot model completed turns cannot contain null values");
    }
    completedTurns = List.copyOf(completedTurns);
  }
}
