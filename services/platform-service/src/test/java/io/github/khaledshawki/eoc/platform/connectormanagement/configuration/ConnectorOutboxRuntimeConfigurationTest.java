package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import io.github.khaledshawki.eoc.platform.observability.metrics.EventPipelineMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorOutboxRuntimeConfigurationTest {

  private final ConnectorOutboxRuntimeConfiguration configuration =
      new ConnectorOutboxRuntimeConfiguration();
  private final PublishConnectorOutboxBatchUseCase useCase = command -> null;
  private final PlatformKafkaProducerProperties producerProperties =
      new PlatformKafkaProducerProperties(Duration.ofSeconds(5));
  private final EventPipelineMetrics metrics = new EventPipelineMetrics(new SimpleMeterRegistry());

  @Test
  void shouldRequireTheClaimLeaseToCoverTheWorstCaseSequentialKafkaBatch() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertThrows(
        IllegalStateException.class,
        () ->
            configuration.connectorOutboxScheduledRelay(
                useCase, kafka, producerProperties, "kafka", 1, 15, metrics));
    assertThrows(
        IllegalStateException.class,
        () ->
            configuration.connectorOutboxScheduledRelay(
                useCase, kafka, producerProperties, "kafka", 2, 30, metrics));
  }

  @Test
  void shouldAllowKafkaPublicationWhenTheClaimLeaseHasBatchHeadroom() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "kafka", 1, 30, metrics));
    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "kafka", 2, 31, metrics));
  }

  @Test
  void shouldNotApplyKafkaTimeoutConstraintToLocalTransport() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "local", 50, 1, metrics));
  }
}
