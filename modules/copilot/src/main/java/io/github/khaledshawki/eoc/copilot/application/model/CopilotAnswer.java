package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CopilotAnswer(String text, List<CopilotAnswerGrounding> grounding) {
  public static final int MAX_TEXT_LENGTH = 20_000;

  public CopilotAnswer {
    Objects.requireNonNull(text, "Copilot answer text cannot be null");
    text = text.strip();
    if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("Copilot answer text must be nonblank and bounded");
    }
    Objects.requireNonNull(grounding, "Copilot answer grounding cannot be null");
    if (grounding.isEmpty() || grounding.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Copilot answer must contain grounding");
    }
    grounding = List.copyOf(grounding);
    if (new HashSet<>(grounding.stream().map(CopilotAnswerGrounding::toolCallId).toList()).size()
        != grounding.size()) {
      throw new IllegalArgumentException("Copilot answer grounding tool call ids must be unique");
    }
  }
}
