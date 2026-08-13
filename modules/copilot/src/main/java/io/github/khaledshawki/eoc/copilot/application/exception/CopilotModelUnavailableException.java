package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotModelUnavailableException extends RuntimeException {
  public CopilotModelUnavailableException(Throwable cause) {
    super("Copilot language model is unavailable", cause);
  }
}
