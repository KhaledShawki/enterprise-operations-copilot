package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CopilotModelTurn(List<CopilotToolObservation> observations) {
  public CopilotModelTurn {
    Objects.requireNonNull(observations, "Copilot model turn observations cannot be null");
    if (observations.isEmpty() || observations.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Copilot model turn must contain observations");
    }
    observations = List.copyOf(observations);
    if (new HashSet<>(observations.stream().map(CopilotToolObservation::callId).toList()).size()
        != observations.size()) {
      throw new IllegalArgumentException("Copilot model turn call ids must be unique");
    }
  }
}
