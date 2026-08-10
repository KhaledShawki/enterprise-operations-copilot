package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eoc.connector-events.transport", havingValue = "kafka")
@EnableConfigurationProperties(ConnectorKafkaConsumerProperties.class)
class KafkaConnectorConsumerConfiguration {

  static final String FAILURE_CODE_HEADER = "eoc-connector-failure-code";
  static final String RETRYABLE_HEADER = "eoc-connector-retryable";

  @Bean
  @ConditionalOnProperty(
      name = "eoc.connector-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  KafkaConnectorIntegrationEventDecoder kafkaConnectorIntegrationEventDecoder(
      JsonMapper jsonMapper, ConnectorKafkaConsumerProperties properties) {
    return new KafkaConnectorIntegrationEventDecoder(jsonMapper, properties.maxEventBytes());
  }

  @Bean
  @ConditionalOnProperty(
      name = "eoc.connector-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  KafkaConnectorIntegrationEventConsumer kafkaConnectorIntegrationEventConsumer(
      KafkaConnectorIntegrationEventDecoder decoder,
      ConsumeConnectorIntegrationEventUseCase useCase) {
    return new KafkaConnectorIntegrationEventConsumer(decoder, useCase);
  }

  private static DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
      KafkaTemplate<String, String> kafkaTemplate, ConnectorKafkaConsumerProperties properties) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> new TopicPartition(properties.dltTopic(), record.partition()));
    recoverer.setVerifyPartition(false);
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(properties.dltSendTimeout());
    recoverer.setHeadersFunction(
        (record, exception) -> {
          FailureDescription failure = describe(exception);
          return new RecordHeaders()
              .add(FAILURE_CODE_HEADER, bytes(failure.failureCode()))
              .add(RETRYABLE_HEADER, bytes(Boolean.toString(failure.retryable())));
        });
    return recoverer;
  }

  private static DefaultErrorHandler errorHandler(
      DeadLetterPublishingRecoverer recoverer, ConnectorKafkaConsumerProperties properties) {
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(properties.retryBackoff().toMillis(), properties.maxAttempts() - 1L));
    errorHandler.addNotRetryableExceptions(TerminalConnectorKafkaConsumptionException.class);
    errorHandler.setResetStateOnRecoveryFailure(true);
    errorHandler.setAckAfterHandle(true);
    return errorHandler;
  }

  @Bean(name = "connectorKafkaListenerContainerFactory")
  @ConditionalOnProperty(
      name = "eoc.connector-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  ConcurrentKafkaListenerContainerFactory<Object, Object> connectorKafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      KafkaTemplate<String, String> kafkaTemplate,
      ConnectorKafkaProperties kafkaProperties,
      ConnectorKafkaConsumerProperties consumerProperties,
      @Value("${spring.kafka.consumer.max-poll-interval:5m}") Duration maxPollInterval) {
    if (kafkaProperties.topic().equals(consumerProperties.dltTopic())) {
      throw new IllegalStateException("Connector Kafka source and DLT topics must be different");
    }
    requireRecoveryBudgetWithinPollInterval(kafkaProperties, consumerProperties, maxPollInterval);

    ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    DefaultErrorHandler connectorKafkaErrorHandler =
        errorHandler(
            deadLetterPublishingRecoverer(kafkaTemplate, consumerProperties), consumerProperties);
    configurer.configure(factory, consumerFactory);
    factory.setBatchListener(false);
    factory.setConcurrency(consumerProperties.concurrency());
    factory.setCommonErrorHandler(connectorKafkaErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setObservationEnabled(true);
    return factory;
  }

  static void requireRecoveryBudgetWithinPollInterval(
      ConnectorKafkaProperties kafkaProperties,
      ConnectorKafkaConsumerProperties consumerProperties,
      Duration maxPollInterval) {
    if (maxPollInterval.isZero() || maxPollInterval.isNegative()) {
      throw new IllegalStateException("Kafka max-poll interval must be positive");
    }
    try {
      Duration recoveryBudget =
          consumerProperties
              .retryBackoff()
              .multipliedBy(consumerProperties.maxAttempts() - 1L)
              .plus(kafkaProperties.maxBlockTimeout())
              .plus(consumerProperties.dltSendTimeout());
      if (recoveryBudget.compareTo(maxPollInterval) >= 0) {
        throw new IllegalStateException(
            "Connector Kafka retry and DLT budget must be shorter than the max-poll interval");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "Connector Kafka recovery budget exceeds the supported duration range", exception);
    }
  }

  private static FailureDescription describe(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof ConnectorKafkaConsumptionException exception) {
        return new FailureDescription(exception.failureCode(), exception.retryable());
      }
      current = current.getCause();
    }
    return new FailureDescription(KafkaConnectorIntegrationEventConsumer.CONSUMPTION_FAILED, true);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private record FailureDescription(String failureCode, boolean retryable) {}
}
