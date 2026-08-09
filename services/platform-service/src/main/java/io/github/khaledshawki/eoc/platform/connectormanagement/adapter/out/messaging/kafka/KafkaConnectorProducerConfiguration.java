package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "eoc.connector-events.transport", havingValue = "kafka")
class KafkaConnectorProducerConfiguration {

  @Bean
  DefaultKafkaProducerFactoryCustomizer connectorKafkaProducerFactoryCustomizer(
      ConnectorKafkaProperties kafkaProperties) {
    return producerFactory ->
        producerFactory.updateConfigs(
            Map.of(
                ProducerConfig.MAX_BLOCK_MS_CONFIG, kafkaProperties.maxBlockTimeout().toMillis()));
  }
}
