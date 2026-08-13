package io.github.khaledshawki.eoc.platform.integration.copilot.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.audit.application.exception.AuditUnavailableException;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.FailureCode;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Grounding;
import io.github.khaledshawki.eoc.audit.application.port.in.RecordCopilotExecutionAuditUseCase;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditedAskCopilotUseCaseTest {
  private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID SOURCE_EVENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000203");

  @Test
  void recordsAttributionDigestsAndGroundingWithoutRawPromptOrAnswer() {
    CapturingAudit audit = new CapturingAudit();
    CopilotExecutionContext executionContext =
        new CopilotExecutionContext(URI.create("https://issuer.example"), "user-123", TENANT_ID);
    CopilotQuestion question =
        new CopilotQuestion("Show overdue receivables", Optional.of(LocalDate.parse("2026-08-13")));
    CopilotAnswer answer =
        new CopilotAnswer(
            "1 overdue invoice: CHF 100.00",
            List.of(
                new CopilotAnswerGrounding(
                    "call-1",
                    CopilotToolName.GET_RECEIVABLES_SUMMARY,
                    List.of(
                        new CopilotEvidence(
                            SOURCE_EVENT_ID, 4, Instant.parse("2026-08-13T12:00:00Z"))))));
    var useCase =
        new AuditedAskCopilotUseCase((context, value) -> answer, audit, () -> EXECUTION_ID);

    CopilotAnswer result = useCase.ask(executionContext, question);

    assertSame(answer, result);
    assertEquals(1, audit.started.size());
    assertEquals(1, audit.succeeded.size());
    assertEquals(0, audit.failed.size());
    assertEquals(
        "b2c4f1084c3499214a35075c714d7f436208c41abeec8b3afc2f8528a3e1bbe6",
        audit.started.getFirst().context.questionSha256());
    assertEquals(
        "334e2ea78897999deba4bc1f111d4524da81c95a5246f7445cdf1f2353f53e90",
        audit.succeeded.getFirst().answerSha256);
    assertEquals(TENANT_ID, audit.started.getFirst().context.tenantId());
    assertEquals("user-123", audit.started.getFirst().context.subject());
    assertEquals(
        LocalDate.parse("2026-08-13"),
        audit.started.getFirst().context.businessDate().orElseThrow());
    assertEquals(
        "get_receivables_summary",
        audit.succeeded.getFirst().groundings.getFirst().toolName().contractName());
    assertEquals(
        SOURCE_EVENT_ID,
        audit.succeeded.getFirst().groundings.getFirst().evidence().getFirst().sourceEventId());
  }

  @Test
  void recordsStableFailureCodeAndRethrowsOriginalExecutionFailure() {
    CapturingAudit audit = new CapturingAudit();
    CopilotModelUnavailableException failure =
        new CopilotModelUnavailableException(new IllegalStateException("provider"));
    var useCase =
        new AuditedAskCopilotUseCase(
            (context, question) -> {
              throw failure;
            },
            audit,
            () -> EXECUTION_ID);

    RuntimeException thrown =
        assertThrows(
            CopilotModelUnavailableException.class,
            () -> useCase.ask(context(), CopilotQuestion.current("Show overdue receivables")));

    assertSame(failure, thrown);
    assertEquals(1, audit.started.size());
    assertEquals(FailureCode.MODEL_UNAVAILABLE, audit.failed.getFirst().failureCode);
    assertEquals(0, audit.succeeded.size());
  }

  @Test
  void successAuditFailureDoesNotCreateFalseCopilotFailureAudit() {
    CapturingAudit audit = new CapturingAudit();
    audit.failSuccess = true;
    CopilotAnswer answer = answer();
    var useCase =
        new AuditedAskCopilotUseCase((context, question) -> answer, audit, () -> EXECUTION_ID);

    assertThrows(
        AuditUnavailableException.class,
        () -> useCase.ask(context(), CopilotQuestion.current("Show overdue receivables")));

    assertEquals(1, audit.started.size());
    assertEquals(0, audit.failed.size());
  }

  @Test
  void failureAuditLossTakesPrecedenceAndPreservesOriginalFailure() {
    CapturingAudit audit = new CapturingAudit();
    audit.failFailure = true;
    CopilotModelUnavailableException executionFailure =
        new CopilotModelUnavailableException(new IllegalStateException("provider"));
    var useCase =
        new AuditedAskCopilotUseCase(
            (context, question) -> {
              throw executionFailure;
            },
            audit,
            () -> EXECUTION_ID);

    AuditUnavailableException thrown =
        assertThrows(
            AuditUnavailableException.class,
            () -> useCase.ask(context(), CopilotQuestion.current("Show overdue receivables")));

    assertEquals(1, thrown.getSuppressed().length);
    assertSame(executionFailure, thrown.getSuppressed()[0]);
  }

  @Test
  void auditStartFailurePreventsCopilotExecution() {
    var audit = new CapturingAudit();
    audit.failStart = true;
    int[] delegateCalls = {0};
    var useCase =
        new AuditedAskCopilotUseCase(
            (context, question) -> {
              delegateCalls[0]++;
              throw new AssertionError("delegate must not run");
            },
            audit,
            () -> EXECUTION_ID);

    assertThrows(
        RuntimeException.class,
        () -> useCase.ask(context(), CopilotQuestion.current("Show overdue receivables")));
    assertEquals(0, delegateCalls[0]);
  }

  private static CopilotAnswer answer() {
    return new CopilotAnswer(
        "1 overdue invoice: CHF 100.00",
        List.of(
            new CopilotAnswerGrounding(
                "call-1", CopilotToolName.GET_RECEIVABLES_SUMMARY, List.of())));
  }

  private static CopilotExecutionContext context() {
    return new CopilotExecutionContext(URI.create("https://issuer.example"), "user-123", TENANT_ID);
  }

  private static final class CapturingAudit implements RecordCopilotExecutionAuditUseCase {
    private final List<StartedCall> started = new ArrayList<>();
    private final List<SucceededCall> succeeded = new ArrayList<>();
    private final List<FailedCall> failed = new ArrayList<>();
    private boolean failStart;
    private boolean failSuccess;
    private boolean failFailure;

    @Override
    public void recordStarted(UUID executionId, Context context) {
      if (failStart) {
        throw new RuntimeException("audit unavailable");
      }
      started.add(new StartedCall(executionId, context));
    }

    @Override
    public void recordSucceeded(
        UUID executionId,
        Context context,
        String answerSha256,
        int answerLength,
        List<Grounding> groundings) {
      if (failSuccess) {
        throw new AuditUnavailableException(new IllegalStateException("success audit"));
      }
      succeeded.add(
          new SucceededCall(executionId, context, answerSha256, answerLength, groundings));
    }

    @Override
    public void recordFailed(UUID executionId, Context context, FailureCode failureCode) {
      if (failFailure) {
        throw new AuditUnavailableException(new IllegalStateException("failure audit"));
      }
      failed.add(new FailedCall(executionId, context, failureCode));
    }
  }

  private record StartedCall(UUID executionId, Context context) {}

  private record SucceededCall(
      UUID executionId,
      Context context,
      String answerSha256,
      int answerLength,
      List<Grounding> groundings) {}

  private record FailedCall(UUID executionId, Context context, FailureCode failureCode) {}
}
