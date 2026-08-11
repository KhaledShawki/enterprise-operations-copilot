package io.github.khaledshawki.eoc.platform.operations.configuration;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import io.github.khaledshawki.eoc.operations.application.service.PublishOperationsOutboxBatchService;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
import io.github.khaledshawki.eoc.platform.operations.adapter.in.scheduling.OperationsOutboxScheduledRelay;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({OperationsKafkaProperties.class, OperationsOutboxProperties.class})
public class OperationsOutboxRuntimeConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "eoc.operations-outbox.relay-enabled",
      havingValue = "true",
      matchIfMissing = false)
  PublishOperationsOutboxBatchUseCase publishOperationsOutboxBatchUseCase(
      OperationsOutboxRepository repository,
      OperationsIntegrationEventPublisher publisher,
      OperationsOutboxProperties properties,
      Clock clock) {
    return new PublishOperationsOutboxBatchService(
        repository,
        publisher,
        new OperationsOutboxPublicationPolicy(properties.maxAttempts(), properties.retryDelay()),
        clock);
  }

  @Bean
  @ConditionalOnProperty(
      name = "eoc.operations-outbox.relay-enabled",
      havingValue = "true",
      matchIfMissing = false)
  OperationsOutboxScheduledRelay operationsOutboxScheduledRelay(
      PublishOperationsOutboxBatchUseCase useCase,
      OperationsKafkaProperties kafkaProperties,
      OperationsOutboxProperties outboxProperties,
      PlatformKafkaProducerProperties producerProperties) {
    requireClaimLeaseExceedsPublicationBudget(
        kafkaProperties, outboxProperties, producerProperties);
    return new OperationsOutboxScheduledRelay(
        useCase,
        "operations-outbox-" + UUID.randomUUID(),
        outboxProperties.batchSize(),
        outboxProperties.claimLease());
  }

  static void requireClaimLeaseExceedsPublicationBudget(
      OperationsKafkaProperties kafkaProperties,
      OperationsOutboxProperties outboxProperties,
      PlatformKafkaProducerProperties producerProperties) {
    try {
      Duration budget =
          producerProperties
              .maxBlockTimeout()
              .plus(kafkaProperties.sendTimeout())
              .multipliedBy(outboxProperties.batchSize());
      if (outboxProperties.claimLease().compareTo(budget) <= 0) {
        throw new IllegalStateException(
            "Operations outbox claim lease must exceed the worst-case sequential Kafka"
                + " publication budget for the configured batch size");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "Operations Kafka publication budget exceeds the supported duration range", exception);
    }
  }
}
