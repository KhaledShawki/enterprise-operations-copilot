package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorOutboxRuntimeConfigurationTest {

  private final ConnectorOutboxRuntimeConfiguration configuration =
      new ConnectorOutboxRuntimeConfiguration();
  private final PublishConnectorOutboxBatchUseCase useCase = command -> null;
  private final PlatformKafkaProducerProperties producerProperties =
      new PlatformKafkaProducerProperties(Duration.ofSeconds(5));

  @Test
  void shouldRequireTheClaimLeaseToCoverTheWorstCaseSequentialKafkaBatch() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertThrows(
        IllegalStateException.class,
        () ->
            configuration.connectorOutboxScheduledRelay(
                useCase, kafka, producerProperties, "kafka", 1, 15));
    assertThrows(
        IllegalStateException.class,
        () ->
            configuration.connectorOutboxScheduledRelay(
                useCase, kafka, producerProperties, "kafka", 2, 30));
  }

  @Test
  void shouldAllowKafkaPublicationWhenTheClaimLeaseHasBatchHeadroom() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "kafka", 1, 30));
    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "kafka", 2, 31));
  }

  @Test
  void shouldNotApplyKafkaTimeoutConstraintToLocalTransport() {
    ConnectorKafkaProperties kafka =
        new ConnectorKafkaProperties("eoc.connector.integration-events", Duration.ofSeconds(10));

    assertNotNull(
        configuration.connectorOutboxScheduledRelay(
            useCase, kafka, producerProperties, "local", 50, 1));
  }
}
