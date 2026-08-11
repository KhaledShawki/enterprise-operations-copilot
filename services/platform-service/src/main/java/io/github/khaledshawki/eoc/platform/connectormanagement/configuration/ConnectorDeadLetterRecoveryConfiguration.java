package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorDeadLetterReplayBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.service.InspectConnectorDeadLettersService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.PublishConnectorDeadLetterReplayBatchService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.RequestConnectorDeadLetterReplayService;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling.ConnectorDeadLetterReplayScheduledRelay;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eoc.connector-events.transport", havingValue = "kafka")
@EnableConfigurationProperties(ConnectorDeadLetterRecoveryProperties.class)
public class ConnectorDeadLetterRecoveryConfiguration {

  private static final String ENABLED_PROPERTY =
      "eoc.connector-events.kafka.dead-letter-recovery.enabled";

  @Bean
  @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true", matchIfMissing = false)
  InspectConnectorDeadLettersUseCase inspectConnectorDeadLettersUseCase(
      ConnectorDeadLetterReader reader, ConnectorDeadLetterRecoveryProperties properties) {
    return new InspectConnectorDeadLettersService(reader, properties.maxPageSize());
  }

  @Bean
  @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true", matchIfMissing = false)
  RequestConnectorDeadLetterReplayUseCase requestConnectorDeadLetterReplayUseCase(
      ConnectorDeadLetterReader reader,
      ConnectorDeadLetterReplayRepository repository,
      Clock clock,
      ConnectorDeadLetterRecoveryProperties properties) {
    return new RequestConnectorDeadLetterReplayService(
        reader, repository, UUID::randomUUID, clock, properties.maxReplayGeneration());
  }

  @Bean
  @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true", matchIfMissing = false)
  PublishConnectorDeadLetterReplayBatchUseCase publishConnectorDeadLetterReplayBatchUseCase(
      ConnectorDeadLetterReplayRepository repository,
      ConnectorDeadLetterReplayPublisher publisher,
      Clock clock,
      ConnectorDeadLetterRecoveryProperties properties) {
    return new PublishConnectorDeadLetterReplayBatchService(
        repository,
        publisher,
        new ConnectorDeadLetterReplayPolicy(properties.maxAttempts(), properties.retryDelay()),
        clock);
  }

  @Bean
  @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true", matchIfMissing = false)
  ConnectorDeadLetterReplayScheduledRelay connectorDeadLetterReplayScheduledRelay(
      PublishConnectorDeadLetterReplayBatchUseCase useCase,
      ConnectorKafkaProperties kafkaProperties,
      ConnectorDeadLetterRecoveryProperties properties) {
    requireClaimLeaseExceedsPublicationBudget(kafkaProperties, properties);
    return new ConnectorDeadLetterReplayScheduledRelay(
        useCase,
        "connector-dlt-replay-" + UUID.randomUUID(),
        properties.batchSize(),
        properties.claimLease());
  }

  static void requireClaimLeaseExceedsPublicationBudget(
      ConnectorKafkaProperties kafkaProperties, ConnectorDeadLetterRecoveryProperties properties) {
    try {
      Duration budget =
          kafkaProperties
              .maxBlockTimeout()
              .plus(kafkaProperties.sendTimeout())
              .multipliedBy(properties.batchSize());
      if (properties.claimLease().compareTo(budget) <= 0) {
        throw new IllegalStateException(
            "Connector DLT replay claim lease must exceed the worst-case sequential Kafka"
                + " publication budget for the configured batch size");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "Connector DLT replay publication budget exceeds the supported duration range",
          exception);
    }
  }
}
