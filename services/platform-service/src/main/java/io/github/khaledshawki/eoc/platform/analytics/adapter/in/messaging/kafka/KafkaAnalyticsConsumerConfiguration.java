package io.github.khaledshawki.eoc.platform.analytics.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.platform.analytics.adapter.messaging.kafka.AnalyticsKafkaHeaders;
import io.github.khaledshawki.eoc.platform.analytics.configuration.AnalyticsKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.analytics.configuration.AnalyticsKafkaProperties;
import io.github.khaledshawki.eoc.platform.messaging.kafka.PlatformKafkaProducerProperties;
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
@ConditionalOnProperty(name = "eoc.analytics-events.transport", havingValue = "kafka")
@EnableConfigurationProperties({
  AnalyticsKafkaProperties.class,
  AnalyticsKafkaConsumerProperties.class
})
class KafkaAnalyticsConsumerConfiguration {

  static final String FAILURE_CODE_HEADER = AnalyticsKafkaHeaders.FAILURE_CODE;
  static final String RETRYABLE_HEADER = AnalyticsKafkaHeaders.RETRYABLE;

  @Bean
  @ConditionalOnProperty(
      name = "eoc.analytics-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  KafkaAnalyticsIntegrationEventDecoder kafkaAnalyticsIntegrationEventDecoder(
      JsonMapper jsonMapper, AnalyticsKafkaConsumerProperties properties) {
    return new KafkaAnalyticsIntegrationEventDecoder(jsonMapper, properties.maxEventBytes());
  }

  @Bean
  @ConditionalOnProperty(
      name = "eoc.analytics-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  KafkaAnalyticsIntegrationEventConsumer kafkaAnalyticsIntegrationEventConsumer(
      KafkaAnalyticsIntegrationEventDecoder decoder,
      ConsumeAnalyticsIntegrationEventUseCase useCase) {
    return new KafkaAnalyticsIntegrationEventConsumer(decoder, useCase);
  }

  @Bean(name = "analyticsKafkaListenerContainerFactory")
  @ConditionalOnProperty(
      name = "eoc.analytics-events.kafka.consumer.enabled",
      havingValue = "true",
      matchIfMissing = true)
  ConcurrentKafkaListenerContainerFactory<Object, Object> analyticsKafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      KafkaTemplate<String, String> kafkaTemplate,
      AnalyticsKafkaProperties kafkaProperties,
      PlatformKafkaProducerProperties producerProperties,
      AnalyticsKafkaConsumerProperties consumerProperties,
      @Value("${spring.kafka.consumer.max-poll-interval:5m}") Duration maxPollInterval) {
    if (kafkaProperties.sourceTopic().equals(consumerProperties.dltTopic())) {
      throw new IllegalStateException("Analytics Kafka source and DLT topics must be different");
    }
    requireRecoveryBudgetWithinPollInterval(
        producerProperties, consumerProperties, maxPollInterval);

    ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    DefaultErrorHandler errorHandler =
        errorHandler(
            deadLetterPublishingRecoverer(kafkaTemplate, consumerProperties), consumerProperties);
    configurer.configure(factory, consumerFactory);
    factory.setBatchListener(false);
    factory.setConcurrency(consumerProperties.concurrency());
    factory.setCommonErrorHandler(errorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setObservationEnabled(true);
    return factory;
  }

  static void requireRecoveryBudgetWithinPollInterval(
      PlatformKafkaProducerProperties producerProperties,
      AnalyticsKafkaConsumerProperties consumerProperties,
      Duration maxPollInterval) {
    if (maxPollInterval.isZero() || maxPollInterval.isNegative()) {
      throw new IllegalStateException("Kafka max-poll interval must be positive");
    }
    try {
      Duration recoveryBudget =
          consumerProperties
              .retryBackoff()
              .multipliedBy(consumerProperties.maxAttempts() - 1L)
              .plus(producerProperties.maxBlockTimeout())
              .plus(consumerProperties.dltSendTimeout());
      if (recoveryBudget.compareTo(maxPollInterval) >= 0) {
        throw new IllegalStateException(
            "Analytics Kafka retry and DLT budget must be shorter than the max-poll interval");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "Analytics Kafka recovery budget exceeds the supported duration range", exception);
    }
  }

  private static DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
      KafkaTemplate<String, String> kafkaTemplate, AnalyticsKafkaConsumerProperties properties) {
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
      DeadLetterPublishingRecoverer recoverer, AnalyticsKafkaConsumerProperties properties) {
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(properties.retryBackoff().toMillis(), properties.maxAttempts() - 1L));
    errorHandler.addNotRetryableExceptions(TerminalAnalyticsKafkaConsumptionException.class);
    errorHandler.setResetStateOnRecoveryFailure(true);
    errorHandler.setAckAfterHandle(true);
    return errorHandler;
  }

  private static FailureDescription describe(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof AnalyticsKafkaConsumptionException exception) {
        return new FailureDescription(exception.failureCode(), exception.retryable());
      }
      current = current.getCause();
    }
    return new FailureDescription(KafkaAnalyticsIntegrationEventConsumer.CONSUMPTION_FAILED, true);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private record FailureDescription(String failureCode, boolean retryable) {}
}
