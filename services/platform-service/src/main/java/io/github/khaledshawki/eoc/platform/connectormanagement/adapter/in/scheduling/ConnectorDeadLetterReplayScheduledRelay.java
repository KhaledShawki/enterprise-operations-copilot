package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorDeadLetterReplayBatchUseCase;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipelineMetrics;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

public final class ConnectorDeadLetterReplayScheduledRelay {

  private final PublishConnectorDeadLetterReplayBatchUseCase useCase;
  private final PublishConnectorDeadLetterReplayBatchCommand command;
  private final EventPipelineMetrics metrics;

  public ConnectorDeadLetterReplayScheduledRelay(
      PublishConnectorDeadLetterReplayBatchUseCase useCase,
      String workerId,
      int batchSize,
      Duration claimLease,
      EventPipelineMetrics metrics) {
    this.useCase = Objects.requireNonNull(useCase, "Replay publication use case cannot be null");
    this.command =
        new PublishConnectorDeadLetterReplayBatchCommand(workerId, batchSize, claimLease);
    this.metrics = Objects.requireNonNull(metrics, "Event pipeline metrics cannot be null");
  }

  @Scheduled(
      initialDelayString =
          "${eoc.connector-events.kafka.dead-letter-recovery.initial-delay-ms:5000}",
      fixedDelayString = "${eoc.connector-events.kafka.dead-letter-recovery.fixed-delay-ms:1000}",
      timeUnit = TimeUnit.MILLISECONDS)
  public void publishNextBatch() {
    PublishConnectorDeadLetterReplayBatchResult result = useCase.publishBatch(command);
    metrics.recordDeadLetterReplay(result.replayed(), result.retriesScheduled() + result.failed());
  }
}
