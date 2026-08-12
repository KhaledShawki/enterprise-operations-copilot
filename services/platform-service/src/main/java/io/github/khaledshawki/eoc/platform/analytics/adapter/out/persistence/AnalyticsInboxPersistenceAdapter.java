package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsInboxAcceptance;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.port.out.AnalyticsIntegrationEventInbox;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
class AnalyticsInboxPersistenceAdapter implements AnalyticsIntegrationEventInbox {

  private static final String EVENT_ID_COLLISION = "analytics-event-id-collision";
  private static final String INBOX_UNAVAILABLE = "analytics-inbox-unavailable";
  private static final String CONTRACT_REJECTED = "analytics-inbox-contract-rejected";
  private static final String INVALID_PAYLOAD = "analytics-inbox-payload-invalid";

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final JsonMapper jsonMapper;

  AnalyticsInboxPersistenceAdapter(JdbcTemplate jdbcTemplate, Clock clock, JsonMapper jsonMapper) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
  }

  @Override
  public AnalyticsInboxAcceptance accept(AnalyticsIntegrationEvent event) {
    Objects.requireNonNull(event, "Analytics integration event cannot be null");
    AnalyticsPersistenceTransactionGuard.requireActive();
    String canonicalPayload = canonicalPayload(event.payload());
    String fingerprint = fingerprint(event, canonicalPayload);
    Instant processedAt = clock.instant();
    String projectionStatus =
        event.projectionPayload() instanceof AnalyticsProjectionPayload.Ignored
            ? "IGNORED"
            : "APPLIED";

    try {
      int inserted =
          jdbcTemplate.update(
              """
              INSERT INTO analytics_inbox_events (
                event_id,
                event_type,
                schema_version,
                tenant_id,
                aggregate_type,
                aggregate_id,
                aggregate_version,
                payload,
                content_fingerprint,
                projection_status,
                occurred_at,
                received_at,
                processed_at
              ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
              ON CONFLICT (event_id) DO NOTHING
              """,
              event.eventId(),
              event.eventType(),
              event.schemaVersion(),
              event.tenantId(),
              event.aggregateType(),
              event.aggregateId(),
              event.aggregateVersion(),
              canonicalPayload,
              fingerprint,
              projectionStatus,
              Timestamp.from(event.occurredAt()),
              Timestamp.from(processedAt),
              Timestamp.from(processedAt));

      if (inserted == 1) {
        return AnalyticsInboxAcceptance.ACCEPTED;
      }
      if (hasIdenticalImmutableContent(event, canonicalPayload, projectionStatus)) {
        return AnalyticsInboxAcceptance.DUPLICATE;
      }
      throw new AnalyticsEventConsumptionException(EVENT_ID_COLLISION, false, null);
    } catch (AnalyticsEventConsumptionException exception) {
      throw exception;
    } catch (DataIntegrityViolationException exception) {
      throw new AnalyticsEventConsumptionException(CONTRACT_REJECTED, false, exception);
    } catch (DataAccessException exception) {
      throw new AnalyticsEventConsumptionException(INBOX_UNAVAILABLE, true, exception);
    }
  }

  private String canonicalPayload(String payload) {
    try {
      JsonNode node = jsonMapper.readTree(payload);
      if (node == null || !node.isObject()) {
        throw new AnalyticsEventConsumptionException(INVALID_PAYLOAD, false, null);
      }
      return jsonMapper.writeValueAsString(node);
    } catch (AnalyticsEventConsumptionException exception) {
      throw exception;
    } catch (JacksonException exception) {
      throw new AnalyticsEventConsumptionException(INVALID_PAYLOAD, false, exception);
    }
  }

  private boolean hasIdenticalImmutableContent(
      AnalyticsIntegrationEvent event, String canonicalPayload, String projectionStatus) {
    Boolean identical =
        jdbcTemplate.queryForObject(
            """
            SELECT event_type = ?
              AND schema_version = ?
              AND tenant_id = ?
              AND aggregate_type = ?
              AND aggregate_id = ?
              AND aggregate_version = ?
              AND payload = CAST(? AS jsonb)
              AND projection_status = ?
              AND occurred_at = ?
            FROM analytics_inbox_events
            WHERE event_id = ?
            """,
            Boolean.class,
            event.eventType(),
            event.schemaVersion(),
            event.tenantId(),
            event.aggregateType(),
            event.aggregateId(),
            event.aggregateVersion(),
            canonicalPayload,
            projectionStatus,
            Timestamp.from(event.occurredAt()),
            event.eventId());
    return Boolean.TRUE.equals(identical);
  }

  private static String fingerprint(AnalyticsIntegrationEvent event, String canonicalPayload) {
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
    update(digest, Long.toString(event.aggregateVersion()));
    update(digest, canonicalPayload);
    update(digest, event.occurredAt().toString());
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
