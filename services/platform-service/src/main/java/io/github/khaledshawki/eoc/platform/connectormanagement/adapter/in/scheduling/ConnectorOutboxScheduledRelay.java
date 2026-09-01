package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipeline;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipelineMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

public final class ConnectorOutboxScheduledRelay {

  private final PublishConnectorOutboxBatchUseCase useCase;
  private final PublishConnectorOutboxBatchCommand command;
  private final EventPipelineMetrics metrics;

  public ConnectorOutboxScheduledRelay(
      PublishConnectorOutboxBatchUseCase useCase,
      String workerId,
      int batchSize,
      Duration claimLease,
      EventPipelineMetrics metrics) {
    this.useCase = Objects.requireNonNull(useCase, "Outbox publication use case cannot be null");
    this.command = new PublishConnectorOutboxBatchCommand(workerId, batchSize, claimLease);
    this.metrics = Objects.requireNonNull(metrics, "Event pipeline metrics cannot be null");
  }

  @Scheduled(
      initialDelayString = "${eoc.connector-outbox.initial-delay-ms:5000}",
      fixedDelayString = "${eoc.connector-outbox.fixed-delay-ms:1000}",
      timeUnit = TimeUnit.MILLISECONDS)
  public void publishNextBatch() {
    Timer.Sample sample = metrics.startTimer();
    PublishConnectorOutboxBatchResult result = useCase.publishBatch(command);
    metrics.recordOutboxPublication(
        EventPipeline.CONNECTOR,
        result.published(),
        result.retriesScheduled() + result.failed(),
        sample);
  }
}
