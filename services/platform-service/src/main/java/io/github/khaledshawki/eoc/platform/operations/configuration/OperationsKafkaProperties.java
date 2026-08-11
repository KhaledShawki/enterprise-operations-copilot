package io.github.khaledshawki.eoc.platform.operations.configuration;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.operations-events.kafka")
public record OperationsKafkaProperties(String topic, Duration sendTimeout) {

  private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]+");

  public OperationsKafkaProperties {
    Objects.requireNonNull(topic, "Operations Kafka topic cannot be null");
    if (topic.length() > 249
        || topic.equals(".")
        || topic.equals("..")
        || !TOPIC.matcher(topic).matches()) {
      throw new IllegalArgumentException("Operations Kafka topic is invalid");
    }
    Objects.requireNonNull(sendTimeout, "Operations Kafka send timeout cannot be null");
    if (sendTimeout.toMillis() < 1
        || sendTimeout.isNegative()
        || sendTimeout.compareTo(Duration.ofMinutes(10)) > 0) {
      throw new IllegalArgumentException(
          "Operations Kafka send timeout must be at least one millisecond and at most ten"
              + " minutes");
    }
  }
}
