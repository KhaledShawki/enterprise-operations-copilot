package io.github.khaledshawki.eoc.platform.audit.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.audit.application.exception.AuditUnavailableException;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Evidence;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.FailureCode;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Grounding;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.ToolName;
import io.github.khaledshawki.eoc.audit.application.port.in.RecordCopilotExecutionAuditUseCase;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "eoc.connector-outbox.relay-enabled=false",
      "eoc.operations-outbox.relay-enabled=false",
      "eoc.analytics-events.transport=disabled"
    })
@Import(TestcontainersConfiguration.class)
class CopilotExecutionAuditPersistenceAdapterIT {
  private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final UUID SOURCE_EVENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000303");

  @Autowired private RecordCopilotExecutionAuditUseCase auditUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearAuditState() {
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_evidence");
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_groundings");
    jdbcTemplate.update("DELETE FROM copilot_execution_audit_events");
  }

  @Test
  void persistsStartAndSuccessWithGroundingEvidenceAndNoRawPayloadColumns() {
    Context context = context();
    auditUseCase.recordStarted(EXECUTION_ID, context);
    auditUseCase.recordSucceeded(
        EXECUTION_ID,
        context,
        "b".repeat(64),
        31,
        List.of(
            new Grounding(
                "call-1",
                ToolName.GET_RECEIVABLE,
                List.of(new Evidence(SOURCE_EVENT_ID, 7, Instant.parse("2026-08-13T12:00:00Z"))))));

    assertEquals(2, count("copilot_execution_audit_events"));
    assertEquals(1, count("copilot_execution_audit_groundings"));
    assertEquals(1, count("copilot_execution_audit_evidence"));
    assertEquals(
        "SUCCEEDED",
        jdbcTemplate.queryForObject(
            """
            SELECT event_type
            FROM copilot_execution_audit_events
            WHERE execution_id = ? AND event_type = 'SUCCEEDED'
            """,
            String.class,
            EXECUTION_ID));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_name = 'copilot_execution_audit_events'
              AND column_name IN ('question', 'question_text', 'answer', 'answer_text')
            """,
            Integer.class));
  }

  @Test
  void rejectsASecondTerminalEventForTheSameExecution() {
    Context context = context();
    auditUseCase.recordStarted(EXECUTION_ID, context);
    auditUseCase.recordSucceeded(
        EXECUTION_ID,
        context,
        "b".repeat(64),
        10,
        List.of(new Grounding("call-1", ToolName.GET_RECEIVABLES_SUMMARY, List.of())));

    assertThrows(
        AuditUnavailableException.class,
        () -> auditUseCase.recordFailed(EXECUTION_ID, context, FailureCode.UNEXPECTED));
    assertEquals(2, count("copilot_execution_audit_events"));
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private static Context context() {
    return new Context(
        URI.create("https://issuer.example"),
        "subject-1",
        TENANT_ID,
        Optional.of(LocalDate.parse("2026-08-13")),
        "a".repeat(64),
        24);
  }
}
