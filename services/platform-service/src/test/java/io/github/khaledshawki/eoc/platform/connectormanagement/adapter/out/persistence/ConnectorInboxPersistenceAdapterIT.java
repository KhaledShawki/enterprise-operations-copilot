package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventType;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  ConnectorInboxPersistenceAdapterIT.FixedClockConfiguration.class
})
class ConnectorInboxPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-03T18:00:00Z");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");
  private static final String TRIGGER_NAME = "reject_connector_projection_for_test";
  private static final String FUNCTION_NAME = "reject_connector_projection_for_test";

  @Autowired private ConnectorIntegrationEventPublisher eventPublisher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    dropFailureTrigger();
    jdbcTemplate.update("DELETE FROM connector_import_run_event_projection");
    jdbcTemplate.update("DELETE FROM connector_inbox_events");
  }

  @AfterEach
  void tearDown() {
    dropFailureTrigger();
  }

  @Test
  void shouldPersistTheInboxReceiptAndProjectionExactlyOnce() {
    ClaimedConnectorOutboxEvent event = completedEvent(EVENT_ID, completedPayload(2));

    eventPublisher.publish(event);
    eventPublisher.publish(event);

    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
    assertEquals(
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.eventType(),
        jdbcTemplate.queryForObject(
            "SELECT event_type FROM connector_inbox_events WHERE event_id = ?",
            String.class,
            EVENT_ID));
    assertEquals(
        64,
        jdbcTemplate.queryForObject(
            "SELECT length(payload_fingerprint) FROM connector_inbox_events WHERE event_id = ?",
            Integer.class,
            EVENT_ID));
  }

  @Test
  void shouldRejectEventIdReuseWithDifferentContent() {
    eventPublisher.publish(completedEvent(EVENT_ID, completedPayload(2)));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> eventPublisher.publish(completedEvent(EVENT_ID, completedPayload(3))));

    assertEquals("connector-event-id-collision", exception.failureCode());
    assertFalse(exception.retryable());
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  @Test
  void shouldRejectUnsupportedContractsBeforeWritingAnInboxReceipt() {
    ClaimedConnectorOutboxEvent unsupported =
        new ClaimedConnectorOutboxEvent(
            EVENT_ID,
            "connector.import-run.completed.v2",
            2,
            TENANT_ID,
            "IMPORT_RUN",
            IMPORT_RUN_ID,
            completedPayload(2),
            NOW.minusSeconds(30),
            1,
            "worker-a",
            NOW.minusSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class, () -> eventPublisher.publish(unsupported));

    assertEquals("unsupported-connector-event-contract", exception.failureCode());
    assertFalse(exception.retryable());
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));
  }

  @Test
  void shouldRejectMalformedPayloadBeforeWritingAnInboxReceipt() {
    String malformedPayload =
        """
        {
          "connectorId": "00000000-0000-0000-0000-000000000083",
          "importType": "CUSTOMERS",
          "importMode": "INCREMENTAL",
          "completionStatus": "COMPLETED",
          "fetched": 2,
          "accepted": 2,
          "rejected": 0,
          "duplicates": 0,
          "attemptCount": 1
        }
        """;

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> eventPublisher.publish(completedEvent(EVENT_ID, malformedPayload)));

    assertEquals("invalid-connector-event-payload", exception.failureCode());
    assertFalse(exception.retryable());
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));
  }

  @Test
  void shouldRejectRetryPayloadWhoseRetryTimestampDoesNotFollowTheEventOccurrence() {
    Instant occurredAt = NOW.minusSeconds(30);
    String invalidRetryPayload =
        """
        {
          "connectorId": "00000000-0000-0000-0000-000000000083",
          "importType": "CUSTOMERS",
          "importMode": "INCREMENTAL",
          "failure": {
            "category": "TIMEOUT",
            "code": "source-timeout"
          },
          "attemptCount": 1,
          "nextRetryAt": "2026-08-03T17:59:30Z"
        }
        """;
    ClaimedConnectorOutboxEvent event =
        new ClaimedConnectorOutboxEvent(
            EVENT_ID,
            ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED.eventType(),
            ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED.schemaVersion(),
            TENANT_ID,
            ConnectorIntegrationEventType.IMPORT_RUN_RETRY_SCHEDULED.aggregateType(),
            IMPORT_RUN_ID,
            invalidRetryPayload,
            occurredAt,
            1,
            "worker-a",
            NOW.minusSeconds(1));

    ConnectorEventPublicationException exception =
        assertThrows(ConnectorEventPublicationException.class, () -> eventPublisher.publish(event));

    assertEquals("invalid-connector-event-payload", exception.failureCode());
    assertFalse(exception.retryable());
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));
  }

  @Test
  void shouldRollBackTheInboxReceiptWhenProjectionHandlingFails() {
    createFailureTrigger(EVENT_ID);

    ConnectorEventPublicationException exception =
        assertThrows(
            ConnectorEventPublicationException.class,
            () -> eventPublisher.publish(completedEvent(EVENT_ID, completedPayload(2))));

    assertEquals("connector-inbox-unavailable", exception.failureCode());
    assertTrue(exception.retryable());
    assertEquals(0, count("connector_inbox_events"));
    assertEquals(0, count("connector_import_run_event_projection"));

    dropFailureTrigger();
    eventPublisher.publish(completedEvent(EVENT_ID, completedPayload(2)));
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private void createFailureTrigger(UUID eventId) {
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION reject_connector_projection_for_test()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $$
        BEGIN
          IF NEW.event_id = '%s'::uuid THEN
            RAISE EXCEPTION 'forced projection failure';
          END IF;
          RETURN NEW;
        END;
        $$
        """
            .formatted(eventId));
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_connector_projection_for_test
        BEFORE INSERT ON connector_import_run_event_projection
        FOR EACH ROW
        EXECUTE FUNCTION reject_connector_projection_for_test()
        """);
  }

  private void dropFailureTrigger() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS " + TRIGGER_NAME + " ON connector_import_run_event_projection");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + FUNCTION_NAME + "()");
  }

  private static ClaimedConnectorOutboxEvent completedEvent(UUID eventId, String payload) {
    return new ClaimedConnectorOutboxEvent(
        eventId,
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.eventType(),
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.schemaVersion(),
        TENANT_ID,
        ConnectorIntegrationEventType.IMPORT_RUN_COMPLETED.aggregateType(),
        IMPORT_RUN_ID,
        payload,
        NOW.minusSeconds(30),
        1,
        "worker-a",
        NOW.minusSeconds(1));
  }

  private static String completedPayload(int accepted) {
    return """
        {
          "connectorId": "00000000-0000-0000-0000-000000000083",
          "importType": "CUSTOMERS",
          "importMode": "INCREMENTAL",
          "status": "COMPLETED",
          "fetchedCount": %d,
          "acceptedCount": %d,
          "rejectedCount": 0,
          "duplicateCount": 0,
          "attemptCount": 1
        }
        """
        .formatted(accepted, accepted);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock connectorInboxClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
