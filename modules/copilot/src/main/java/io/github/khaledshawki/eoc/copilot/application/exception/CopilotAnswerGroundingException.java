package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotAnswerGroundingException extends RuntimeException {
  public CopilotAnswerGroundingException() {
    super("Copilot answer is not grounded in executed tool results");
  }
}
