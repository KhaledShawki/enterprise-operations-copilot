package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotModelProtocolException extends RuntimeException {
  public CopilotModelProtocolException() {
    super("Copilot language model returned an invalid response");
  }

  public CopilotModelProtocolException(Throwable cause) {
    super("Copilot language model returned an invalid response", cause);
  }
}
