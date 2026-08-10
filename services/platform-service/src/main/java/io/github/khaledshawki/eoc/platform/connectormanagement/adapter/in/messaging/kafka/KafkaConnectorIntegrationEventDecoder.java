package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.messaging.kafka;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class KafkaConnectorIntegrationEventDecoder {

  static final String INVALID_ENVELOPE = "kafka-event-envelope-invalid";
  static final String EVENT_TOO_LARGE = "kafka-event-too-large";

  private static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "eventId",
          "eventType",
          "schemaVersion",
          "tenantId",
          "aggregateType",
          "aggregateId",
          "payload",
          "occurredAt");

  private final JsonMapper jsonMapper;
  private final int maxEventBytes;

  KafkaConnectorIntegrationEventDecoder(JsonMapper jsonMapper, int maxEventBytes) {
    if (jsonMapper == null) {
      throw new NullPointerException("JSON mapper cannot be null");
    }
    if (maxEventBytes < 1) {
      throw new IllegalArgumentException("Maximum event bytes must be positive");
    }
    this.jsonMapper = jsonMapper;
    this.maxEventBytes = maxEventBytes;
  }

  ConnectorIntegrationEventEnvelope decode(String value) {
    if (value == null || value.isBlank()) {
      throw terminal(INVALID_ENVELOPE, null);
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > maxEventBytes) {
      throw terminal(EVENT_TOO_LARGE, null);
    }

    try {
      JsonNode root = jsonMapper.readTree(value);
      if (root == null
          || !root.isObject()
          || !Set.copyOf(root.propertyNames()).equals(ENVELOPE_FIELDS)) {
        throw new IllegalArgumentException("Kafka event must contain exactly the envelope fields");
      }
      JsonNode payload = required(root, "payload");
      if (!payload.isObject()) {
        throw new IllegalArgumentException("Kafka event payload must be a JSON object");
      }
      JsonNode schemaVersion = required(root, "schemaVersion");
      if (!schemaVersion.isIntegralNumber() || !schemaVersion.canConvertToInt()) {
        throw new IllegalArgumentException("Kafka event schema version must be an integer");
      }

      return new ConnectorIntegrationEventEnvelope(
          parseText(root, "eventId", UUID::fromString),
          text(root, "eventType"),
          schemaVersion.intValue(),
          parseText(root, "tenantId", UUID::fromString),
          text(root, "aggregateType"),
          parseText(root, "aggregateId", UUID::fromString),
          jsonMapper.writeValueAsString(payload),
          parseText(root, "occurredAt", Instant::parse));
    } catch (ConnectorKafkaConsumptionException exception) {
      throw exception;
    } catch (JacksonException | IllegalArgumentException | DateTimeParseException exception) {
      throw terminal(INVALID_ENVELOPE, exception);
    }
  }

  private static JsonNode required(JsonNode root, String name) {
    JsonNode node = root.get(name);
    if (node == null || node.isNull()) {
      throw new IllegalArgumentException("Kafka event field is missing: " + name);
    }
    return node;
  }

  private static String text(JsonNode root, String name) {
    JsonNode node = required(root, name);
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Kafka event field must be text: " + name);
    }
    return node.textValue();
  }

  private static <T> T parseText(JsonNode root, String name, Function<String, T> parser) {
    return parser.apply(text(root, name));
  }

  private static TerminalConnectorKafkaConsumptionException terminal(
      String failureCode, Throwable cause) {
    return new TerminalConnectorKafkaConsumptionException(failureCode, cause);
  }
}
