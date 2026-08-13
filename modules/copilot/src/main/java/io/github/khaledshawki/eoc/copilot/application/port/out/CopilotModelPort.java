package io.github.khaledshawki.eoc.copilot.application.port.out;

import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelRequest;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;

public interface CopilotModelPort {
  CopilotModelResponse generate(CopilotModelRequest request);
}
