package io.github.khaledshawki.eoc.platform.operations.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OperationsOutboxRuntimeConfigurationTest {

  @Test
  void requiresTheClaimLeaseToCoverTheWorstCaseSequentialKafkaBatch() {
    OperationsKafkaProperties kafka =
        new OperationsKafkaProperties("operations.events", Duration.ofSeconds(10));
    PlatformKafkaProducerProperties producer =
        new PlatformKafkaProducerProperties(Duration.ofSeconds(5));

    assertThrows(
        IllegalStateException.class,
        () ->
            OperationsOutboxRuntimeConfiguration.requireClaimLeaseExceedsPublicationBudget(
                kafka, outbox(1, Duration.ofSeconds(15)), producer));
    assertThrows(
        IllegalStateException.class,
        () ->
            OperationsOutboxRuntimeConfiguration.requireClaimLeaseExceedsPublicationBudget(
                kafka, outbox(2, Duration.ofSeconds(30)), producer));
  }

  @Test
  void acceptsAClaimLeaseWithStrictPublicationHeadroom() {
    OperationsKafkaProperties kafka =
        new OperationsKafkaProperties("operations.events", Duration.ofSeconds(10));
    PlatformKafkaProducerProperties producer =
        new PlatformKafkaProducerProperties(Duration.ofSeconds(5));

    assertDoesNotThrow(
        () ->
            OperationsOutboxRuntimeConfiguration.requireClaimLeaseExceedsPublicationBudget(
                kafka, outbox(2, Duration.ofSeconds(31)), producer));
  }

  private static OperationsOutboxProperties outbox(int batchSize, Duration claimLease) {
    return new OperationsOutboxProperties(
        true, batchSize, claimLease, 5, Duration.ofMinutes(1), 5000, 1000);
  }
}
