package io.github.khaledshawki.eoc.platform.operations.adapter.in.scheduling;

import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

public final class OperationsOutboxScheduledRelay {

  private final PublishOperationsOutboxBatchUseCase useCase;
  private final PublishOperationsOutboxBatchCommand command;

  public OperationsOutboxScheduledRelay(
      PublishOperationsOutboxBatchUseCase useCase,
      String workerId,
      int batchSize,
      Duration claimLease) {
    this.useCase = Objects.requireNonNull(useCase, "Outbox publication use case cannot be null");
    this.command = new PublishOperationsOutboxBatchCommand(workerId, batchSize, claimLease);
  }

  @Scheduled(
      initialDelayString = "${eoc.operations-outbox.initial-delay-ms:5000}",
      fixedDelayString = "${eoc.operations-outbox.fixed-delay-ms:1000}",
      timeUnit = TimeUnit.MILLISECONDS)
  public void publishNextBatch() {
    useCase.publishBatch(command);
  }
}
