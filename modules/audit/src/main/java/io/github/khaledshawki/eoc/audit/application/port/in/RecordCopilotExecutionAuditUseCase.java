package io.github.khaledshawki.eoc.audit.application.port.in;

import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.FailureCode;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Grounding;
import java.util.List;
import java.util.UUID;

public interface RecordCopilotExecutionAuditUseCase {
  void recordStarted(UUID executionId, Context context);

  void recordSucceeded(
      UUID executionId,
      Context context,
      String answerSha256,
      int answerLength,
      List<Grounding> groundings);

  void recordFailed(UUID executionId, Context context, FailureCode failureCode);
}
