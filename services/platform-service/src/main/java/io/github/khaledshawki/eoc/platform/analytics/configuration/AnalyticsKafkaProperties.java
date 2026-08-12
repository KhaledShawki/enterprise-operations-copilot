package io.github.khaledshawki.eoc.platform.analytics.configuration;

import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eoc.analytics-events.kafka")
public record AnalyticsKafkaProperties(String sourceTopic) {

  private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9._-]+");

  public AnalyticsKafkaProperties {
    sourceTopic = requireTopic(sourceTopic, "Analytics Kafka source topic");
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
