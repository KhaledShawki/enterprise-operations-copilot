package io.github.khaledshawki.eoc.copilot.application.exception;

public final class CopilotOrchestrationLimitExceededException extends RuntimeException {
  public CopilotOrchestrationLimitExceededException() {
    super("Copilot orchestration exceeded its bounded execution budget");
  }
}
