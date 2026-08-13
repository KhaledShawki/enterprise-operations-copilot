package io.github.khaledshawki.eoc.audit.application.service;

import io.github.khaledshawki.eoc.audit.application.exception.AuditUnavailableException;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Context;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Failed;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.FailureCode;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Grounding;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Started;
import io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent.Succeeded;
import io.github.khaledshawki.eoc.audit.application.port.in.RecordCopilotExecutionAuditUseCase;
import io.github.khaledshawki.eoc.audit.application.port.out.AppendCopilotExecutionAuditEventPort;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CopilotExecutionAuditService implements RecordCopilotExecutionAuditUseCase {
  private final AppendCopilotExecutionAuditEventPort appendPort;
  private final Clock clock;
  private final Supplier<UUID> eventIdSupplier;

  public CopilotExecutionAuditService(
      AppendCopilotExecutionAuditEventPort appendPort,
      Clock clock,
      Supplier<UUID> eventIdSupplier) {
    this.appendPort = Objects.requireNonNull(appendPort, "Audit append port cannot be null");
    this.clock = Objects.requireNonNull(clock, "Audit clock cannot be null");
    this.eventIdSupplier =
        Objects.requireNonNull(eventIdSupplier, "Audit event id supplier cannot be null");
  }

  @Override
  public void recordStarted(UUID executionId, Context context) {
    append(new Started(nextEventId(), executionId, context, clock.instant()));
  }

  @Override
  public void recordSucceeded(
      UUID executionId,
      Context context,
      String answerSha256,
      int answerLength,
      List<Grounding> groundings) {
    append(
        new Succeeded(
            nextEventId(),
            executionId,
            context,
            answerSha256,
            answerLength,
            groundings,
            clock.instant()));
  }

  @Override
  public void recordFailed(UUID executionId, Context context, FailureCode failureCode) {
    append(new Failed(nextEventId(), executionId, context, failureCode, clock.instant()));
  }

  private UUID nextEventId() {
    return Objects.requireNonNull(eventIdSupplier.get(), "Audit event id supplier returned null");
  }

  private void append(
      io.github.khaledshawki.eoc.audit.application.model.CopilotExecutionAuditEvent event) {
    try {
      appendPort.append(event);
    } catch (AuditUnavailableException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AuditUnavailableException(exception);
    }
  }
}
