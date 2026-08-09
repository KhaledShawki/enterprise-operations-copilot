package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.messaging.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventConsumptionException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventEnvelope;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalConnectorIntegrationEventPublisherTest {

  @Test
  void shouldDeliverTheExactBrokerNeutralEnvelopeToTheLocalInbox() {
    AtomicReference<ConnectorIntegrationEventEnvelope> consumed = new AtomicReference<>();
    LocalConnectorIntegrationEventPublisher publisher =
        new LocalConnectorIntegrationEventPublisher(consumed::set);
    ConnectorIntegrationEventEnvelope event = event();

    publisher.publish(event);

    assertSame(event, consumed.get());
  }

  @Test
  void shouldTranslateInboxFailuresIntoPublicationFailuresWithoutLosingClassification() {
    ConnectorEventConsumptionException consumptionFailure =
        new ConnectorEventConsumptionException(
            "connector-inbox-unavailable", true, new IllegalStateException("database unavailable"));
    LocalConnectorIntegrationEventPublisher publisher =
        new LocalConnectorIntegrationEventPublisher(
            event -> {
              throw consumptionFailure;
            });

    ConnectorEventPublicationException exception =
        assertThrows(ConnectorEventPublicationException.class, () -> publisher.publish(event()));

    assertEquals("connector-inbox-unavailable", exception.failureCode());
    assertTrue(exception.retryable());
    assertSame(consumptionFailure, exception.getCause());
  }

  private static ConnectorIntegrationEventEnvelope event() {
    return new ConnectorIntegrationEventEnvelope(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "connector.import-run.completed.v1",
        1,
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        "IMPORT_RUN",
        UUID.fromString("00000000-0000-0000-0000-000000000003"),
        "{\"status\":\"COMPLETED\"}",
        Instant.parse("2026-08-09T18:00:00Z"));
  }
}
