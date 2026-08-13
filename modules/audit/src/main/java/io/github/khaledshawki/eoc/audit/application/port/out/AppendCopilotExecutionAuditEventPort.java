package io.github.khaledshawki.eoc.audit.application.port.out;

import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent;

public interface AppendCopilotExecutionAuditEventPort {
  void append(CopilotExecutionAuditEvent event);
}
