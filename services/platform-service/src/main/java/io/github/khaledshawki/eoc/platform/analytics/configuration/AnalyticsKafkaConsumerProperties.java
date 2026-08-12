package io.github.khaledshawki.eoc.platform.analytics.configuration;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.analytics-events.kafka.consumer")
public record AnalyticsKafkaConsumerProperties(
    boolean enabled,
    String groupId,
    String dltTopic,
    int maxAttempts,
    Duration retryBackoff,
    Duration dltSendTimeout,
    int maxEventBytes,
    int concurrency) {

  private static final Pattern GROUP_ID = Pattern.compile("[A-Za-z0-9._-]+");

  public AnalyticsKafkaConsumerProperties {
    Objects.requireNonNull(groupId, "Analytics Kafka consumer group id cannot be null");
    if (groupId.length() > 255 || !GROUP_ID.matcher(groupId).matches()) {
      throw new IllegalArgumentException("Analytics Kafka consumer group id is invalid");
    }
    dltTopic = AnalyticsKafkaProperties.requireTopic(dltTopic, "Analytics Kafka DLT topic");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("Analytics Kafka max attempts must be positive");
    }
    retryBackoff = requireNonNegative(retryBackoff, "Analytics Kafka retry backoff");
    dltSendTimeout = requirePositive(dltSendTimeout, "Analytics Kafka DLT send timeout");
    if (maxEventBytes < 1) {
      throw new IllegalArgumentException("Analytics Kafka max event bytes must be positive");
    }
    if (concurrency < 1) {
      throw new IllegalArgumentException("Analytics Kafka consumer concurrency must be positive");
    }
  }

  private static Duration requirePositive(Duration value, String description) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(description + " must be positive");
    }
    return value;
  }

  private static Duration requireNonNegative(Duration value, String description) {
    Objects.requireNonNull(value, description + " cannot be null");
    if (value.isNegative()) {
      throw new IllegalArgumentException(description + " cannot be negative");
    }
    return value;
  }
}
