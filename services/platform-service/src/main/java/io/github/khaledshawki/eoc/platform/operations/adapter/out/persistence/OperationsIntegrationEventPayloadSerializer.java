package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventPayload;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class OperationsIntegrationEventPayloadSerializer {

  private final JsonMapper jsonMapper;

  OperationsIntegrationEventPayloadSerializer(JsonMapper jsonMapper) {
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
  }

  String serialize(OperationsIntegrationEventPayload payload) {
    Objects.requireNonNull(payload, "Operations integration event payload cannot be null");
    try {
      return jsonMapper.writeValueAsString(payload);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Operations integration event payload cannot be serialized", exception);
    }
  }
}
