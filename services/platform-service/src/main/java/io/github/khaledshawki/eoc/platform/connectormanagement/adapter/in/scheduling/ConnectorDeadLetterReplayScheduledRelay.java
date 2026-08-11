package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorDeadLetterReplayBatchUseCase;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

public final class ConnectorDeadLetterReplayScheduledRelay {

  private final PublishConnectorDeadLetterReplayBatchUseCase useCase;
  private final PublishConnectorDeadLetterReplayBatchCommand command;

  public ConnectorDeadLetterReplayScheduledRelay(
      PublishConnectorDeadLetterReplayBatchUseCase useCase,
      String workerId,
      int batchSize,
      Duration claimLease) {
    this.useCase = Objects.requireNonNull(useCase, "Replay publication use case cannot be null");
    this.command =
        new PublishConnectorDeadLetterReplayBatchCommand(workerId, batchSize, claimLease);
  }

  @Scheduled(
      initialDelayString =
          "${eoc.connector-events.kafka.dead-letter-recovery.initial-delay-ms:5000}",
      fixedDelayString = "${eoc.connector-events.kafka.dead-letter-recovery.fixed-delay-ms:1000}",
      timeUnit = TimeUnit.MILLISECONDS)
  public void publishNextBatch() {
    useCase.publishBatch(command);
  }
}
