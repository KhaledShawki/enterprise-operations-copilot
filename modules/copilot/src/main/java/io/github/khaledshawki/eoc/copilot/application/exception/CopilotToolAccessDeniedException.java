package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotToolAccessDeniedException extends RuntimeException {
  public CopilotToolAccessDeniedException() {
    super("Copilot tool access denied");
  }
}
