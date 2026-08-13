package io.github.khaledshawki.eoc.platform.integration.copilot.audit;

import io.github.khaledshawki.eoc.audit.application.exception.AuditUnavailableException;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Evidence;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.FailureCode;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Grounding;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.ToolName;
import io.github.khaledshawki.eoc.audit.application.port.in.RecordCopilotExecutionAuditUseCase;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotAnswerGroundingException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolAccessDeniedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataCorruptedException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataNotFoundException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolDataUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.InvalidCopilotToolArgumentsException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolName;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class AuditedAskCopilotUseCase implements AskCopilotUseCase {
  private final AskCopilotUseCase delegate;
  private final RecordCopilotExecutionAuditUseCase auditUseCase;
  private final Supplier<UUID> executionIdSupplier;

  public AuditedAskCopilotUseCase(
      AskCopilotUseCase delegate,
      RecordCopilotExecutionAuditUseCase auditUseCase,
      Supplier<UUID> executionIdSupplier) {
    this.delegate = Objects.requireNonNull(delegate, "Copilot delegate cannot be null");
    this.auditUseCase =
        Objects.requireNonNull(auditUseCase, "Copilot audit use case cannot be null");
    this.executionIdSupplier =
        Objects.requireNonNull(executionIdSupplier, "Copilot execution id supplier cannot be null");
  }

  @Override
  public CopilotAnswer ask(CopilotExecutionContext context, CopilotQuestion question) {
    Objects.requireNonNull(context, "Copilot execution context cannot be null");
    Objects.requireNonNull(question, "Copilot question cannot be null");

    UUID executionId =
        Objects.requireNonNull(
            executionIdSupplier.get(), "Copilot execution id supplier returned null");
    Context auditContext = auditContext(context, question);
    auditUseCase.recordStarted(executionId, auditContext);

    CopilotAnswer answer;
    try {
      answer = delegate.ask(context, question);
    } catch (RuntimeException executionFailure) {
      try {
        auditUseCase.recordFailed(executionId, auditContext, failureCode(executionFailure));
      } catch (AuditUnavailableException auditFailure) {
        auditFailure.addSuppressed(executionFailure);
        throw auditFailure;
      }
      throw executionFailure;
    }

    auditUseCase.recordSucceeded(
        executionId,
        auditContext,
        sha256(answer.text()),
        answer.text().length(),
        answer.grounding().stream().map(AuditedAskCopilotUseCase::grounding).toList());
    return answer;
  }

  static FailureCode failureCode(RuntimeException exception) {
    if (exception instanceof CopilotToolAccessDeniedException) {
      return FailureCode.ACCESS_DENIED;
    }
    if (exception instanceof InvalidCopilotToolArgumentsException) {
      return FailureCode.INVALID_ARGUMENTS;
    }
    if (exception instanceof CopilotToolDataNotFoundException) {
      return FailureCode.NOT_FOUND;
    }
    if (exception instanceof CopilotToolDataUnavailableException) {
      return FailureCode.DATA_UNAVAILABLE;
    }
    if (exception instanceof CopilotToolDataCorruptedException) {
      return FailureCode.DATA_CORRUPTION;
    }
    if (exception instanceof CopilotModelProtocolException) {
      return FailureCode.MODEL_PROTOCOL;
    }
    if (exception instanceof CopilotModelUnavailableException) {
      return FailureCode.MODEL_UNAVAILABLE;
    }
    if (exception instanceof CopilotOrchestrationLimitExceededException) {
      return FailureCode.ORCHESTRATION_LIMIT;
    }
    if (exception instanceof CopilotAnswerGroundingException) {
      return FailureCode.ANSWER_GROUNDING;
    }
    return FailureCode.UNEXPECTED;
  }

  private static Context auditContext(CopilotExecutionContext context, CopilotQuestion question) {
    return new Context(
        context.issuer(),
        context.subject(),
        context.tenantId(),
        question.businessDate(),
        sha256(question.text()),
        question.text().length());
  }

  private static Grounding grounding(
      io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding grounding) {
    List<Evidence> evidence =
        grounding.sourceEvidence().stream()
            .map(
                value ->
                    new Evidence(value.eventId(), value.aggregateVersion(), value.occurredAt()))
            .toList();
    return new Grounding(grounding.toolCallId(), toolName(grounding.toolName()), evidence);
  }

  private static ToolName toolName(CopilotToolName toolName) {
    return switch (toolName) {
      case GET_RECEIVABLE -> ToolName.GET_RECEIVABLE;
      case LIST_RECEIVABLES -> ToolName.LIST_RECEIVABLES;
      case GET_RECEIVABLES_SUMMARY -> ToolName.GET_RECEIVABLES_SUMMARY;
    };
  }

  private static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
