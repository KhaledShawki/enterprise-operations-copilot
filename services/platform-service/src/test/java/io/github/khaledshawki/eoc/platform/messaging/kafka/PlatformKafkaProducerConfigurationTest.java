package io.github.khaledshawki.eoc.platform.messaging.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class PlatformKafkaProducerConfigurationTest {

  @Test
  void appliesTheSharedBoundedMetadataAndBufferWaitToTheProducerFactory() {
    PlatformKafkaProducerConfiguration configuration = new PlatformKafkaProducerConfiguration();
    DefaultKafkaProducerFactoryCustomizer customizer =
        configuration.platformKafkaProducerFactoryCustomizer(
            new PlatformKafkaProducerProperties(Duration.ofSeconds(7)));
    @SuppressWarnings("unchecked")
    DefaultKafkaProducerFactory<Object, Object> producerFactory =
        mock(DefaultKafkaProducerFactory.class);

    customizer.customize(producerFactory);

    verify(producerFactory).updateConfigs(Map.of(ProducerConfig.MAX_BLOCK_MS_CONFIG, 7000L));
  }
}
