package io.github.khaledshawki.eoc.copilot.application.port.out;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;

@FunctionalInterface
public interface CopilotReceivablesAuthorizationPort {
  boolean mayReadReceivables(CopilotExecutionContext context);
}
