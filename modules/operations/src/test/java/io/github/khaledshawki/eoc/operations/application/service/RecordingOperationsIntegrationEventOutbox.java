package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecordingOperationsIntegrationEventOutbox implements OperationsIntegrationEventOutbox {

  final List<OperationsIntegrationEvent> events = new ArrayList<>();
  private final Map<StreamKey, Long> versions = new HashMap<>();

  @Override
  public OperationsIntegrationEvent append(PendingOperationsIntegrationEvent pendingEvent) {
    StreamKey stream =
        new StreamKey(
            pendingEvent.tenantId(), pendingEvent.aggregateType(), pendingEvent.aggregateId());
    long version = versions.merge(stream, 1L, Math::addExact);
    OperationsIntegrationEvent event =
        pendingEvent.materialize(new UUID(0L, events.size() + 1L), version);
    events.add(event);
    return event;
  }

  private record StreamKey(UUID tenantId, String aggregateType, UUID aggregateId) {}
}
