package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorOutboxRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.service.PublishConnectorOutboxBatchService;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.scheduling.ConnectorOutboxScheduledRelay;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ConnectorOutboxRuntimeConfiguration {

  @Bean
  PublishConnectorOutboxBatchUseCase publishConnectorOutboxBatchUseCase(
      ConnectorOutboxRepository connectorOutboxRepository,
      ConnectorIntegrationEventPublisher connectorIntegrationEventPublisher,
      Clock clock) {
    return new PublishConnectorOutboxBatchService(
        connectorOutboxRepository,
        connectorIntegrationEventPublisher,
        new ConnectorOutboxPublicationPolicy(5, Duration.ofMinutes(1)),
        clock);
  }

  @Bean
  @ConditionalOnProperty(
      name = "eoc.connector-outbox.relay-enabled",
      havingValue = "true",
      matchIfMissing = false)
  ConnectorOutboxScheduledRelay connectorOutboxScheduledRelay(
      PublishConnectorOutboxBatchUseCase useCase,
      @Value("${eoc.connector-outbox.batch-size:50}") int batchSize,
      @Value("${eoc.connector-outbox.claim-lease-seconds:30}") long claimLeaseSeconds) {
    return new ConnectorOutboxScheduledRelay(
        useCase,
        "connector-outbox-" + UUID.randomUUID(),
        batchSize,
        Duration.ofSeconds(claimLeaseSeconds));
  }
}
