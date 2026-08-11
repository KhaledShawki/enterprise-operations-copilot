package io.github.khaledshawki.eoc.platform.messaging.kafka;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.kafka.producer")
public record PlatformKafkaProducerProperties(Duration maxBlockTimeout) {

  public PlatformKafkaProducerProperties {
    Objects.requireNonNull(maxBlockTimeout, "Kafka producer max-block timeout cannot be null");
    if (maxBlockTimeout.toMillis() < 1
        || maxBlockTimeout.isNegative()
        || maxBlockTimeout.compareTo(Duration.ofMinutes(10)) > 0) {
      throw new IllegalArgumentException(
          "Kafka producer max-block timeout must be at least one millisecond and at most ten"
              + " minutes");
    }
  }
}
