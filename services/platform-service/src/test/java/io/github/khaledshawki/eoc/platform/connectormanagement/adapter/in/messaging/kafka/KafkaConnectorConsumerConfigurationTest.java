package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KafkaConnectorConsumerConfigurationTest {

  @Test
  void acceptsARecoveryBudgetWithMaxPollHeadroom() {
    assertDoesNotThrow(
        () ->
            KafkaConnectorConsumerConfiguration.requireRecoveryBudgetWithinPollInterval(
                kafkaProperties(), consumerProperties(), Duration.ofMinutes(5)));
  }

  @Test
  void rejectsARecoveryBudgetThatCanCausePredictableConsumerRebalances() {
    assertThrows(
        IllegalStateException.class,
        () ->
            KafkaConnectorConsumerConfiguration.requireRecoveryBudgetWithinPollInterval(
                kafkaProperties(), consumerProperties(), Duration.ofSeconds(18)));
    assertThrows(
        IllegalStateException.class,
        () ->
            KafkaConnectorConsumerConfiguration.requireRecoveryBudgetWithinPollInterval(
                kafkaProperties(), consumerProperties(), Duration.ZERO));
  }

  private static PlatformKafkaProducerProperties kafkaProperties() {
    return new PlatformKafkaProducerProperties(Duration.ofSeconds(5));
  }

  private static ConnectorKafkaConsumerProperties consumerProperties() {
    return new ConnectorKafkaConsumerProperties(
        true,
        "consumer-group",
        "events.dlt",
        4,
        Duration.ofSeconds(1),
        Duration.ofSeconds(10),
        1_048_576,
        3);
  }
}
