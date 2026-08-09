package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.connector-events.kafka")
public record ConnectorKafkaProperties(
    String topic, Duration sendTimeout, Duration maxBlockTimeout) {

  private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]+");

  public ConnectorKafkaProperties {
    Objects.requireNonNull(topic, "Connector Kafka topic cannot be null");
    if (topic.length() > 249
        || topic.equals(".")
        || topic.equals("..")
        || !TOPIC.matcher(topic).matches()) {
      throw new IllegalArgumentException("Connector Kafka topic is invalid");
    }

    Objects.requireNonNull(sendTimeout, "Connector Kafka send timeout cannot be null");
    if (sendTimeout.isZero() || sendTimeout.isNegative()) {
      throw new IllegalArgumentException("Connector Kafka send timeout must be positive");
    }

    Objects.requireNonNull(maxBlockTimeout, "Connector Kafka max-block timeout cannot be null");
    if (maxBlockTimeout.isZero() || maxBlockTimeout.isNegative()) {
      throw new IllegalArgumentException("Connector Kafka max-block timeout must be positive");
    }
  }
}
