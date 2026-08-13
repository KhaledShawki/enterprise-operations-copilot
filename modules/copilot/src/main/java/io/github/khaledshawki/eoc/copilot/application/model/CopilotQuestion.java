package io.github.khaledshawki.eoc.copilot.application.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record CopilotQuestion(String text, Optional<LocalDate> businessDate) {
  public static final int MAX_LENGTH = 2_000;

  public CopilotQuestion {
    Objects.requireNonNull(text, "Copilot question cannot be null");
    text = text.strip();
    if (text.isEmpty() || text.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("Copilot question must be nonblank and bounded");
    }
    Objects.requireNonNull(businessDate, "Copilot question business date container cannot be null");
  }

  public static CopilotQuestion current(String text) {
    return new CopilotQuestion(text, Optional.empty());
  }
}
