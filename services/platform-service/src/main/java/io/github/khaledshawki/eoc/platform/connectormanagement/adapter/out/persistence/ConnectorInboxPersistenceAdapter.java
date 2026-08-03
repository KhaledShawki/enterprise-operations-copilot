package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorEventPublicationException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ConnectorIntegrationEventType;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunCompletedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunFailedPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.event.ImportRunRetryScheduledPayload;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
class ConnectorInboxPersistenceAdapter implements ConnectorIntegrationEventPublisher {

  private static final String UNSUPPORTED_CONTRACT = "unsupported-connector-event-contract";
  private static final String INVALID_PAYLOAD = "invalid-connector-event-payload";
  private static final String EVENT_ID_COLLISION = "connector-event-id-collision";
  private static final String INBOX_UNAVAILABLE = "connector-inbox-unavailable";

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final JsonMapper jsonMapper;

  ConnectorInboxPersistenceAdapter(JdbcTemplate jdbcTemplate, Clock clock, JsonMapper jsonMapper) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
  }

  @Override
  @Transactional
  public void publish(ClaimedConnectorOutboxEvent event) {
    Objects.requireNonNull(event, "Claimed connector outbox event cannot be null");
    ConnectorIntegrationEventType eventType = requireSupportedContract(event);
    requireValidPayload(event, eventType);

    try {
      consumeOnce(event);
    } catch (ConnectorEventPublicationException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw new ConnectorEventPublicationException(INBOX_UNAVAILABLE, true, exception);
    }
  }

  private void requireValidPayload(
      ClaimedConnectorOutboxEvent event, ConnectorIntegrationEventType eventType) {
    try {
      ConnectorIntegrationEventPayload payload =
          switch (eventType) {
            case IMPORT_RUN_COMPLETED ->
                jsonMapper.readValue(event.payload(), ImportRunCompletedPayload.class);
            case IMPORT_RUN_FAILED ->
                jsonMapper.readValue(event.payload(), ImportRunFailedPayload.class);
            case IMPORT_RUN_RETRY_SCHEDULED ->
                jsonMapper.readValue(event.payload(), ImportRunRetryScheduledPayload.class);
          };
      new ConnectorIntegrationEvent(
          event.eventId(),
          eventType,
          event.tenantId(),
          event.aggregateType(),
          event.aggregateId(),
          event.occurredAt(),
          payload);
    } catch (JacksonException | IllegalArgumentException exception) {
      throw new ConnectorEventPublicationException(INVALID_PAYLOAD, false, exception);
    }
  }

  private void consumeOnce(ClaimedConnectorOutboxEvent event) {
    String fingerprint = fingerprint(event);
    Instant processedAt = clock.instant();
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO connector_inbox_events (
              event_id,
              event_type,
              schema_version,
              tenant_id,
              aggregate_type,
              aggregate_id,
              payload,
              payload_fingerprint,
              occurred_at,
              received_at,
              processed_at
            ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """,
            event.eventId(),
            event.eventType(),
            event.schemaVersion(),
            event.tenantId(),
            event.aggregateType(),
            event.aggregateId(),
            event.payload(),
            fingerprint,
            Timestamp.from(event.occurredAt()),
            Timestamp.from(processedAt),
            Timestamp.from(processedAt));

    if (inserted == 0) {
      String storedFingerprint =
          jdbcTemplate.queryForObject(
              "SELECT payload_fingerprint FROM connector_inbox_events WHERE event_id = ?",
              String.class,
              event.eventId());
      if (!fingerprint.equals(storedFingerprint)) {
        throw new ConnectorEventPublicationException(EVENT_ID_COLLISION, false, null);
      }
      return;
    }

    jdbcTemplate.update(
        """
        INSERT INTO connector_import_run_event_projection (
          event_id,
          event_type,
          schema_version,
          tenant_id,
          import_run_id,
          payload,
          occurred_at,
          projected_at
        ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
        """,
        event.eventId(),
        event.eventType(),
        event.schemaVersion(),
        event.tenantId(),
        event.aggregateId(),
        event.payload(),
        Timestamp.from(event.occurredAt()),
        Timestamp.from(processedAt));
  }

  private static ConnectorIntegrationEventType requireSupportedContract(
      ClaimedConnectorOutboxEvent event) {
    for (ConnectorIntegrationEventType type : ConnectorIntegrationEventType.values()) {
      if (type.eventType().equals(event.eventType())
          && type.schemaVersion() == event.schemaVersion()
          && type.aggregateType().equals(event.aggregateType())) {
        return type;
      }
    }
    throw new ConnectorEventPublicationException(UNSUPPORTED_CONTRACT, false, null);
  }

  private static String fingerprint(ClaimedConnectorOutboxEvent event) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }

    update(digest, event.eventId().toString());
    update(digest, event.eventType());
    update(digest, Integer.toString(event.schemaVersion()));
    update(digest, event.tenantId().toString());
    update(digest, event.aggregateType());
    update(digest, event.aggregateId().toString());
    update(digest, event.payload());
    update(digest, event.occurredAt().toString());
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
