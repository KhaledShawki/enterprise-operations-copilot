package io.github.khaledshawki.eoc.platform.messaging.kafka;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformKafkaProducerProperties.class)
class PlatformKafkaProducerConfiguration {

  @Bean
  DefaultKafkaProducerFactoryCustomizer platformKafkaProducerFactoryCustomizer(
      PlatformKafkaProducerProperties properties) {
    return producerFactory ->
        producerFactory.updateConfigs(
            Map.of(ProducerConfig.MAX_BLOCK_MS_CONFIG, properties.maxBlockTimeout().toMillis()));
  }
}
