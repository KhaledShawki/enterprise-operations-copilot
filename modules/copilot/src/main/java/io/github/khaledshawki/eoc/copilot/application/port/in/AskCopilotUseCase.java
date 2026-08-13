package io.github.khaledshawki.eoc.copilot.application.port.in;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;

public interface AskCopilotUseCase {
  CopilotAnswer ask(CopilotExecutionContext context, CopilotQuestion question);
}
