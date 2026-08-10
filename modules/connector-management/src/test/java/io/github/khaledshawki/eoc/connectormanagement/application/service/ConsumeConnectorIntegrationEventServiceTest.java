package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConsumeConnectorIntegrationEventServiceTest {

  @Test
  void delegatesTheBrokerNeutralEnvelopeToTheInbox() {
    AtomicReference<ConnectorIntegrationEventEnvelope> consumedEvent = new AtomicReference<>();
    ConnectorIntegrationEventEnvelope event = event();

    new ConsumeConnectorIntegrationEventService(consumedEvent::set).consume(event);

    assertSame(event, consumedEvent.get());
  }

  @Test
  void rejectsNullBeforeCallingTheOutputPort() {
    AtomicReference<ConnectorIntegrationEventEnvelope> consumedEvent = new AtomicReference<>();
    ConsumeConnectorIntegrationEventService service =
        new ConsumeConnectorIntegrationEventService(consumedEvent::set);

    NullPointerException exception =
        assertThrows(NullPointerException.class, () -> service.consume(null));

    assertEquals("Connector integration event cannot be null", exception.getMessage());
    assertNull(consumedEvent.get());
  }

  private static ConnectorIntegrationEventEnvelope event() {
    return new ConnectorIntegrationEventEnvelope(
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        "connector.import-run.completed",
        1,
        UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        "IMPORT_RUN",
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
        "{}",
        Instant.parse("2026-01-15T10:00:00Z"));
  }
}
