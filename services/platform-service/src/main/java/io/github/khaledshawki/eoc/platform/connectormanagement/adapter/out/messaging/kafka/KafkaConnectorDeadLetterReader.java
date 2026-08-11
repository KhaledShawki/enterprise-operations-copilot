package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterMalformedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterOffsetOutOfRangeException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterUnavailableException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import io.github.khaledshawki.eoc.platform.connectormanagement.adapter.messaging.kafka.ConnectorKafkaHeaders;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorDeadLetterRecoveryProperties;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaConsumerProperties;
import io.github.khaledshawki.eoc.platform.connectormanagement.configuration.ConnectorKafkaProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
    "'${eoc.connector-events.transport:local}' == 'kafka' and "
        + "${eoc.connector-events.kafka.dead-letter-recovery.enabled:false}")
final class KafkaConnectorDeadLetterReader implements ConnectorDeadLetterReader {

  private static final String DLT_HEADER_PREFIX = "kafka_dlt-";
  private static final Set<String> REPLAY_CONTROL_HEADERS =
      Set.of(
          ConnectorKafkaHeaders.FAILURE_CODE,
          ConnectorKafkaHeaders.RETRYABLE,
          ConnectorKafkaHeaders.REPLAY_REQUEST_ID,
          ConnectorKafkaHeaders.REPLAY_GENERATION,
          ConnectorKafkaHeaders.REPLAY_DLT_PARTITION,
          ConnectorKafkaHeaders.REPLAY_DLT_OFFSET);

  private final ConsumerFactory<Object, Object> consumerFactory;
  private final String deadLetterTopic;
  private final String sourceTopic;
  private final Duration timeout;
  private final int maxEventBytes;

  KafkaConnectorDeadLetterReader(
      ConsumerFactory<Object, Object> consumerFactory,
      ConnectorKafkaProperties kafkaProperties,
      ConnectorKafkaConsumerProperties consumerProperties,
      ConnectorDeadLetterRecoveryProperties recoveryProperties) {
    this.consumerFactory =
        Objects.requireNonNull(consumerFactory, "Consumer factory cannot be null");
    this.sourceTopic =
        Objects.requireNonNull(kafkaProperties, "Kafka properties cannot be null").topic();
    ConnectorKafkaConsumerProperties requiredConsumerProperties =
        Objects.requireNonNull(consumerProperties, "Consumer properties cannot be null");
    this.deadLetterTopic = requiredConsumerProperties.dltTopic();
    this.maxEventBytes = requiredConsumerProperties.maxEventBytes();
    this.timeout =
        Objects.requireNonNull(recoveryProperties, "Recovery properties cannot be null")
            .inspectionTimeout();
  }

  @Override
  public List<ConnectorDeadLetterPartition> listPartitions() {
    try (Consumer<Object, Object> consumer = consumerFactory.createConsumer()) {
      List<TopicPartition> partitions =
          consumer.partitionsFor(deadLetterTopic, timeout).stream()
              .map(PartitionInfo::partition)
              .sorted()
              .map(partition -> new TopicPartition(deadLetterTopic, partition))
              .toList();
      Map<TopicPartition, Long> beginningOffsets = consumer.beginningOffsets(partitions, timeout);
      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions, timeout);
      return partitions.stream()
          .map(
              partition ->
                  new ConnectorDeadLetterPartition(
                      partition.partition(),
                      requiredOffset(beginningOffsets, partition),
                      requiredOffset(endOffsets, partition)))
          .toList();
    } catch (KafkaException exception) {
      throw unavailable("Connector DLT partition metadata is unavailable", exception);
    }
  }

  @Override
  public ConnectorDeadLetterPage readPage(int partition, long fromOffset, int limit) {
    ConnectorDeadLetterReference reference =
        new ConnectorDeadLetterReference(partition, fromOffset);
    TopicPartition topicPartition = new TopicPartition(deadLetterTopic, partition);
    try (Consumer<Object, Object> consumer = consumerFactory.createConsumer()) {
      consumer.assign(List.of(topicPartition));
      OffsetRange range = offsetRange(consumer, topicPartition);
      requireRetained(reference, range, true);
      if (fromOffset == range.endOffset()) {
        return new ConnectorDeadLetterPage(
            partition, fromOffset, fromOffset, range.endOffset(), List.of());
      }

      consumer.seek(topicPartition, fromOffset);
      List<ConnectorDeadLetterRecord> records = new ArrayList<>(limit);
      long nextOffset = fromOffset;
      Instant deadline = Instant.now().plus(timeout);
      while (records.size() < limit && nextOffset < range.endOffset()) {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
          break;
        }
        ConsumerRecords<Object, Object> polled = consumer.poll(remaining);
        if (polled.isEmpty()) {
          nextOffset =
              Math.max(nextOffset, Math.min(consumer.position(topicPartition), range.endOffset()));
          break;
        }
        for (ConsumerRecord<Object, Object> record : polled.records(topicPartition)) {
          if (record.offset() >= range.endOffset()) {
            break;
          }
          records.add(map(record));
          nextOffset = record.offset() + 1;
          if (records.size() == limit) {
            break;
          }
        }
        if (records.size() < limit) {
          nextOffset =
              Math.max(nextOffset, Math.min(consumer.position(topicPartition), range.endOffset()));
        }
      }
      return new ConnectorDeadLetterPage(
          partition, fromOffset, nextOffset, range.endOffset(), records);
    } catch (ConnectorDeadLetterMalformedException
        | ConnectorDeadLetterOffsetOutOfRangeException exception) {
      throw exception;
    } catch (KafkaException exception) {
      throw unavailable("Connector DLT records are unavailable", exception);
    }
  }

  @Override
  public Optional<ConnectorDeadLetterRecord> find(ConnectorDeadLetterReference reference) {
    Objects.requireNonNull(reference, "Dead-letter reference cannot be null");
    TopicPartition topicPartition = new TopicPartition(deadLetterTopic, reference.partition());
    try (Consumer<Object, Object> consumer = consumerFactory.createConsumer()) {
      consumer.assign(List.of(topicPartition));
      OffsetRange range = offsetRange(consumer, topicPartition);
      requireRetained(reference, range, false);
      consumer.seek(topicPartition, reference.offset());
      ConsumerRecords<Object, Object> records = consumer.poll(timeout);
      for (ConsumerRecord<Object, Object> record : records.records(topicPartition)) {
        if (record.offset() == reference.offset()) {
          return Optional.of(map(record));
        }
        if (record.offset() > reference.offset()) {
          return Optional.empty();
        }
      }
      return Optional.empty();
    } catch (ConnectorDeadLetterMalformedException
        | ConnectorDeadLetterOffsetOutOfRangeException exception) {
      throw exception;
    } catch (KafkaException exception) {
      throw unavailable("Connector DLT record is unavailable", exception);
    }
  }

  private ConnectorDeadLetterRecord map(ConsumerRecord<Object, Object> record) {
    ConnectorDeadLetterReference reference =
        new ConnectorDeadLetterReference(record.partition(), record.offset());
    if (record.key() != null && !(record.key() instanceof String)) {
      throw malformed(reference, "record key is not text");
    }
    if (record.value() != null && !(record.value() instanceof String)) {
      throw malformed(reference, "record value is not text");
    }
    int keyBytes =
        record.key() == null ? 0 : ((String) record.key()).getBytes(StandardCharsets.UTF_8).length;
    int valueBytes =
        record.value() == null
            ? 0
            : ((String) record.value()).getBytes(StandardCharsets.UTF_8).length;
    if ((long) keyBytes + valueBytes > maxEventBytes) {
      throw malformed(reference, "record key and value exceed the configured size bound");
    }

    String originalTopic = requiredTextHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC);
    int originalPartition = requiredIntHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION);
    if (!sourceTopic.equals(originalTopic) || originalPartition != record.partition()) {
      throw malformed(reference, "source topic or partition evidence does not match routing");
    }

    return new ConnectorDeadLetterRecord(
        reference,
        deadLetterTopic,
        Optional.ofNullable((String) record.key()),
        Optional.ofNullable((String) record.value()),
        originalTopic,
        originalPartition,
        requiredLongHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
        Instant.ofEpochMilli(requiredLongHeader(record, KafkaHeaders.DLT_ORIGINAL_TIMESTAMP)),
        requiredTextHeader(record, ConnectorKafkaHeaders.FAILURE_CODE),
        requiredBooleanHeader(record, ConnectorKafkaHeaders.RETRYABLE),
        truncate(requiredTextHeader(record, KafkaHeaders.DLT_EXCEPTION_FQCN), 500),
        optionalTextHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)
            .map(value -> truncate(value, 2000)),
        optionalIntegerTextHeader(record, ConnectorKafkaHeaders.REPLAY_GENERATION).orElse(0),
        replayHeaders(record));
  }

  private static List<ConnectorDeadLetterHeader> replayHeaders(ConsumerRecord<?, ?> record) {
    List<ConnectorDeadLetterHeader> headers = new ArrayList<>();
    for (Header header : record.headers()) {
      if (!header.key().startsWith(DLT_HEADER_PREFIX)
          && !REPLAY_CONTROL_HEADERS.contains(header.key())) {
        headers.add(ConnectorDeadLetterHeader.fromBytes(header.key(), header.value()));
      }
    }
    return List.copyOf(headers);
  }

  private OffsetRange offsetRange(Consumer<?, ?> consumer, TopicPartition partition) {
    long beginning =
        requiredOffset(consumer.beginningOffsets(List.of(partition), timeout), partition);
    long end = requiredOffset(consumer.endOffsets(List.of(partition), timeout), partition);
    return new OffsetRange(beginning, end);
  }

  private static void requireRetained(
      ConnectorDeadLetterReference reference, OffsetRange range, boolean allowEnd) {
    boolean beyondEnd =
        allowEnd ? reference.offset() > range.endOffset() : reference.offset() >= range.endOffset();
    if (reference.offset() < range.beginningOffset() || beyondEnd) {
      throw new ConnectorDeadLetterOffsetOutOfRangeException(
          reference, range.beginningOffset(), range.endOffset());
    }
  }

  private static long requiredOffset(Map<TopicPartition, Long> offsets, TopicPartition partition) {
    Long offset = offsets.get(partition);
    if (offset == null) {
      throw new ConnectorDeadLetterUnavailableException(
          "Kafka did not return an offset for connector DLT partition " + partition.partition());
    }
    return offset;
  }

  private static String requiredTextHeader(ConsumerRecord<?, ?> record, String name) {
    return optionalTextHeader(record, name)
        .filter(value -> !value.isBlank())
        .orElseThrow(() -> malformed(reference(record), "required header " + name + " is absent"));
  }

  private static Optional<String> optionalTextHeader(ConsumerRecord<?, ?> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null || header.value() == null
        ? Optional.empty()
        : Optional.of(new String(header.value(), StandardCharsets.UTF_8));
  }

  private static int requiredIntHeader(ConsumerRecord<?, ?> record, String name) {
    byte[] value = requiredHeader(record, name);
    if (value.length != Integer.BYTES) {
      throw malformed(reference(record), "header " + name + " is not a 32-bit integer");
    }
    return ByteBuffer.wrap(value).getInt();
  }

  private static long requiredLongHeader(ConsumerRecord<?, ?> record, String name) {
    byte[] value = requiredHeader(record, name);
    if (value.length != Long.BYTES) {
      throw malformed(reference(record), "header " + name + " is not a 64-bit integer");
    }
    return ByteBuffer.wrap(value).getLong();
  }

  private static boolean requiredBooleanHeader(ConsumerRecord<?, ?> record, String name) {
    String value = requiredTextHeader(record, name);
    if (!value.equals("true") && !value.equals("false")) {
      throw malformed(reference(record), "header " + name + " is not a boolean");
    }
    return Boolean.parseBoolean(value);
  }

  private static Optional<Integer> optionalIntegerTextHeader(
      ConsumerRecord<?, ?> record, String name) {
    return optionalTextHeader(record, name)
        .map(
            value -> {
              try {
                return Integer.valueOf(value);
              } catch (NumberFormatException exception) {
                throw malformed(reference(record), "header " + name + " is not an integer");
              }
            });
  }

  private static byte[] requiredHeader(ConsumerRecord<?, ?> record, String name) {
    Header header = record.headers().lastHeader(name);
    if (header == null || header.value() == null) {
      throw malformed(reference(record), "required header " + name + " is absent");
    }
    return header.value();
  }

  private static ConnectorDeadLetterReference reference(ConsumerRecord<?, ?> record) {
    return new ConnectorDeadLetterReference(record.partition(), record.offset());
  }

  private static ConnectorDeadLetterMalformedException malformed(
      ConnectorDeadLetterReference reference, String reason) {
    return new ConnectorDeadLetterMalformedException(reference, reason);
  }

  private static ConnectorDeadLetterUnavailableException unavailable(
      String message, KafkaException exception) {
    return new ConnectorDeadLetterUnavailableException(message, exception);
  }

  private static String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private record OffsetRange(long beginningOffset, long endOffset) {}
}
