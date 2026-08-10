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
    topic = requireTopic(topic, "Connector Kafka topic");

    Objects.requireNonNull(sendTimeout, "Connector Kafka send timeout cannot be null");
    if (sendTimeout.isZero() || sendTimeout.isNegative()) {
      throw new IllegalArgumentException("Connector Kafka send timeout must be positive");
    }

    Objects.requireNonNull(maxBlockTimeout, "Connector Kafka max-block timeout cannot be null");
    if (maxBlockTimeout.isZero() || maxBlockTimeout.isNegative()) {
      throw new IllegalArgumentException("Connector Kafka max-block timeout must be positive");
    }
  }

  static String requireTopic(String value, String description) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.length() > 249
        || value.equals(".")
        || value.equals("..")
        || !TOPIC.matcher(value).matches()) {
      throw new IllegalArgumentException(description + " is invalid");
    }
    return value;
  }
}
