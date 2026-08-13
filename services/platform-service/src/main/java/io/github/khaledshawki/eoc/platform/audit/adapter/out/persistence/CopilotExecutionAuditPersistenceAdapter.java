package io.github.khaledshawki.eoc.platform.audit.adapter.out.persistence;

import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Failed;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Succeeded;
import io.github.khaledshawki.eoc.audit.application.port.out.AppendCopilotExecutionAuditEventPort;
import java.sql.Date;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class CopilotExecutionAuditPersistenceAdapter
    implements AppendCopilotExecutionAuditEventPort {

  private static final String INSERT_EVENT =
      """
      INSERT INTO copilot_execution_audit_events (
        audit_event_id,
        execution_id,
        event_type,
        issuer,
        subject,
        tenant_id,
        business_date,
        question_sha256,
        question_length,
        answer_sha256,
        answer_length,
        failure_code,
        occurred_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public CopilotExecutionAuditPersistenceAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void append(CopilotExecutionAuditEvent event) {
    String eventType = eventType(event);
    String answerSha256 = event instanceof Succeeded succeeded ? succeeded.answerSha256() : null;
    Integer answerLength = event instanceof Succeeded succeeded ? succeeded.answerLength() : null;
    String failureCode = event instanceof Failed failed ? failed.failureCode().name() : null;
    var context = event.context();

    jdbcTemplate.update(
        INSERT_EVENT,
        event.auditEventId(),
        event.executionId(),
        eventType,
        context.issuer().toString(),
        context.subject(),
        context.tenantId(),
        context.businessDate().map(Date::valueOf).orElse(null),
        context.questionSha256(),
        context.questionLength(),
        answerSha256,
        answerLength,
        failureCode,
        Timestamp.from(event.occurredAt()));

    if (event instanceof Succeeded succeeded) {
      appendGroundings(succeeded);
    }
  }

  private void appendGroundings(Succeeded event) {
    for (int groundingIndex = 0; groundingIndex < event.groundings().size(); groundingIndex++) {
      var grounding = event.groundings().get(groundingIndex);
      jdbcTemplate.update(
          """
          INSERT INTO copilot_execution_audit_groundings (
            audit_event_id, grounding_index, tool_call_id, tool_name
          ) VALUES (?, ?, ?, ?)
          """,
          event.auditEventId(),
          groundingIndex,
          grounding.toolCallId(),
          grounding.toolName().contractName());

      for (int evidenceIndex = 0; evidenceIndex < grounding.evidence().size(); evidenceIndex++) {
        var evidence = grounding.evidence().get(evidenceIndex);
        jdbcTemplate.update(
            """
            INSERT INTO copilot_execution_audit_evidence (
              audit_event_id,
              grounding_index,
              evidence_index,
              source_event_id,
              aggregate_version,
              occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            event.auditEventId(),
            groundingIndex,
            evidenceIndex,
            evidence.sourceEventId(),
            evidence.aggregateVersion(),
            Timestamp.from(evidence.occurredAt()));
      }
    }
  }

  private static String eventType(CopilotExecutionAuditEvent event) {
    if (event instanceof CopilotExecutionAuditEvent.Started) {
      return "STARTED";
    }
    if (event instanceof Succeeded) {
      return "SUCCEEDED";
    }
    if (event instanceof Failed) {
      return "FAILED";
    }
    throw new IllegalArgumentException("Unsupported Copilot audit event type");
  }
}
