package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import java.util.Objects;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;

final class ConnectorKafkaPublicationFailureClassifier {

  static final String BROKER_RETRYABLE = "kafka-broker-retryable-error";
  static final String PUBLISH_REJECTED = "kafka-publish-rejected";
  static final String PUBLISH_FAILED = "kafka-publish-failed";

  private ConnectorKafkaPublicationFailureClassifier() {}

  static ConnectorEventPublicationException classify(Throwable failure) {
    Throwable cause = Objects.requireNonNull(failure, "Kafka publication failure cannot be null");
    if (contains(
        cause,
        AuthenticationException.class,
        AuthorizationException.class,
        InvalidTopicException.class,
        RecordTooLargeException.class,
        SerializationException.class)) {
      return new ConnectorEventPublicationException(PUBLISH_REJECTED, false, cause);
    }
    if (contains(cause, RetriableException.class)) {
      return new ConnectorEventPublicationException(BROKER_RETRYABLE, true, cause);
    }
    if (contains(cause, KafkaException.class)) {
      return new ConnectorEventPublicationException(PUBLISH_FAILED, false, cause);
    }
    return new ConnectorEventPublicationException(PUBLISH_FAILED, false, cause);
  }

  @SafeVarargs
  private static boolean contains(Throwable failure, Class<? extends Throwable>... types) {
    Throwable current = failure;
    while (current != null) {
      for (Class<? extends Throwable> type : types) {
        if (type.isInstance(current)) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
