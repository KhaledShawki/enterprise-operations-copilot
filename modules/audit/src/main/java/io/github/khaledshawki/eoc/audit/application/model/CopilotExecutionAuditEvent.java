package io.github.khaledshawki.eoc.audit.application.model;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface CopilotExecutionAuditEvent
    permits CopilotExecutionAuditEvent.Started,
        CopilotExecutionAuditEvent.Succeeded,
        CopilotExecutionAuditEvent.Failed {

  int MAX_QUESTION_LENGTH = 2_000;
  int MAX_ANSWER_LENGTH = 20_000;
  int MAX_GROUNDINGS = 3;

  UUID auditEventId();

  UUID executionId();

  Context context();

  Instant occurredAt();

  record Context(
      URI issuer,
      String subject,
      UUID tenantId,
      Optional<LocalDate> businessDate,
      String questionSha256,
      int questionLength) {
    public Context {
      Objects.requireNonNull(issuer, "Audit issuer cannot be null");
      if (!issuer.isAbsolute() || issuer.toString().length() > 2_048) {
        throw new IllegalArgumentException("Audit issuer must be an absolute bounded URI");
      }
      Objects.requireNonNull(subject, "Audit subject cannot be null");
      subject = subject.strip();
      if (subject.isEmpty() || subject.length() > 512) {
        throw new IllegalArgumentException("Audit subject must be nonblank and bounded");
      }
      Objects.requireNonNull(tenantId, "Audit tenant id cannot be null");
      Objects.requireNonNull(businessDate, "Audit business date container cannot be null");
      requireSha256(questionSha256, "Audit question digest");
      if (questionLength < 1 || questionLength > MAX_QUESTION_LENGTH) {
        throw new IllegalArgumentException("Audit question length must be bounded");
      }
    }
  }

  enum ToolName {
    GET_RECEIVABLE("get_receivable"),
    LIST_RECEIVABLES("list_receivables"),
    GET_RECEIVABLES_SUMMARY("get_receivables_summary");

    private final String contractName;

    ToolName(String contractName) {
      this.contractName = contractName;
    }

    public String contractName() {
      return contractName;
    }
  }

  enum FailureCode {
    ACCESS_DENIED,
    INVALID_ARGUMENTS,
    NOT_FOUND,
    DATA_UNAVAILABLE,
    DATA_CORRUPTION,
    MODEL_PROTOCOL,
    MODEL_UNAVAILABLE,
    ORCHESTRATION_LIMIT,
    ANSWER_GROUNDING,
    UNEXPECTED
  }

  record Evidence(UUID sourceEventId, long aggregateVersion, Instant occurredAt) {
    public Evidence {
      Objects.requireNonNull(sourceEventId, "Audit evidence event id cannot be null");
      if (aggregateVersion < 1) {
        throw new IllegalArgumentException("Audit evidence aggregate version must be positive");
      }
      Objects.requireNonNull(occurredAt, "Audit evidence occurrence time cannot be null");
    }
  }

  record Grounding(String toolCallId, ToolName toolName, List<Evidence> evidence) {
    public Grounding {
      Objects.requireNonNull(toolCallId, "Audit grounding tool call id cannot be null");
      toolCallId = toolCallId.strip();
      if (toolCallId.isEmpty() || toolCallId.length() > 128) {
        throw new IllegalArgumentException("Audit grounding tool call id must be bounded");
      }
      Objects.requireNonNull(toolName, "Audit grounding tool name cannot be null");
      Objects.requireNonNull(evidence, "Audit grounding evidence cannot be null");
      if (evidence.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Audit grounding evidence cannot contain null values");
      }
      evidence = List.copyOf(evidence);
    }
  }

  record Started(UUID auditEventId, UUID executionId, Context context, Instant occurredAt)
      implements CopilotExecutionAuditEvent {
    public Started {
      validateCommon(auditEventId, executionId, context, occurredAt);
    }
  }

  record Succeeded(
      UUID auditEventId,
      UUID executionId,
      Context context,
      String answerSha256,
      int answerLength,
      List<Grounding> groundings,
      Instant occurredAt)
      implements CopilotExecutionAuditEvent {
    public Succeeded {
      validateCommon(auditEventId, executionId, context, occurredAt);
      requireSha256(answerSha256, "Audit answer digest");
      if (answerLength < 1 || answerLength > MAX_ANSWER_LENGTH) {
        throw new IllegalArgumentException("Audit answer length must be bounded");
      }
      Objects.requireNonNull(groundings, "Audit success groundings cannot be null");
      if (groundings.isEmpty()
          || groundings.size() > MAX_GROUNDINGS
          || groundings.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Audit success must contain bounded groundings");
      }
      groundings = List.copyOf(groundings);
      if (new HashSet<>(groundings.stream().map(Grounding::toolCallId).toList()).size()
          != groundings.size()) {
        throw new IllegalArgumentException("Audit grounding tool call ids must be unique");
      }
    }
  }

  record Failed(
      UUID auditEventId,
      UUID executionId,
      Context context,
      FailureCode failureCode,
      Instant occurredAt)
      implements CopilotExecutionAuditEvent {
    public Failed {
      validateCommon(auditEventId, executionId, context, occurredAt);
      Objects.requireNonNull(failureCode, "Audit failure code cannot be null");
    }
  }

  private static void validateCommon(
      UUID auditEventId, UUID executionId, Context context, Instant occurredAt) {
    Objects.requireNonNull(auditEventId, "Audit event id cannot be null");
    Objects.requireNonNull(executionId, "Audit execution id cannot be null");
    Objects.requireNonNull(context, "Audit context cannot be null");
    Objects.requireNonNull(occurredAt, "Audit occurrence time cannot be null");
  }

  private static void requireSha256(String value, String label) {
    Objects.requireNonNull(value, label + " cannot be null");
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException(label + " must be lowercase SHA-256 hex");
    }
  }
}
