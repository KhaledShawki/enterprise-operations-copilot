package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConnectorOutboxScheduledRelayTest {

  @Test
  void shouldInvokeTheOutboxUseCaseWithTheConfiguredWorkerClaim() {
    AtomicReference<PublishConnectorOutboxBatchCommand> command = new AtomicReference<>();
    ConnectorOutboxScheduledRelay relay =
        new ConnectorOutboxScheduledRelay(
            value -> {
              command.set(value);
              return PublishConnectorOutboxBatchResult.empty();
            },
            "connector-outbox-test-worker",
            25,
            Duration.ofSeconds(30),
            new EventPipelineMetrics(new SimpleMeterRegistry()));

    relay.publishNextBatch();

    assertEquals(
        new PublishConnectorOutboxBatchCommand(
            "connector-outbox-test-worker", 25, Duration.ofSeconds(30)),
        command.get());
  }
}
