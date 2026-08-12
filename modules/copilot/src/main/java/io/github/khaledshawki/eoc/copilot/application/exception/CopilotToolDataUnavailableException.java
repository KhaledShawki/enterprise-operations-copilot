package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotToolDataUnavailableException extends RuntimeException {
  public CopilotToolDataUnavailableException(Throwable cause) {
    super("Copilot analytics data is unavailable", cause);
  }
}
