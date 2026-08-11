package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxClaimLostException;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.event.PendingOperationsIntegrationEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
class OperationsOutboxPersistenceAdapter
    implements OperationsIntegrationEventOutbox, OperationsOutboxRepository {

  private static final String ALLOCATE_VERSION_SQL =
      """
      INSERT INTO operations_event_stream_versions (
        tenant_id,
        aggregate_type,
        aggregate_id,
        last_version,
        created_at,
        updated_at
      ) VALUES (?, ?, ?, 1, ?, ?)
      ON CONFLICT (tenant_id, aggregate_type, aggregate_id)
      DO UPDATE SET
        last_version = operations_event_stream_versions.last_version + 1,
        updated_at = EXCLUDED.updated_at
      RETURNING last_version
      """;

  private static final String CLAIM_SQL =
      """
      WITH candidates AS (
        SELECT event.event_id
        FROM operations_outbox_events event
        WHERE (
          (
            event.publish_status IN ('PENDING', 'RETRY_SCHEDULED')
            AND event.next_publish_at <= ?
          ) OR (
            event.publish_status = 'CLAIMED'
            AND event.claimed_at <= ?
          )
        )
        AND NOT EXISTS (
          SELECT 1
          FROM operations_outbox_events predecessor
          WHERE predecessor.tenant_id = event.tenant_id
            AND predecessor.aggregate_type = event.aggregate_type
            AND predecessor.aggregate_id = event.aggregate_id
            AND predecessor.aggregate_version < event.aggregate_version
            AND predecessor.publish_status <> 'PUBLISHED'
        )
        ORDER BY event.next_publish_at, event.occurred_at, event.event_id
        LIMIT ?
        FOR UPDATE OF event SKIP LOCKED
      ), claimed AS (
        UPDATE operations_outbox_events event
        SET publish_status = 'CLAIMED',
            publish_attempt_count = event.publish_attempt_count + 1,
            generation_attempt_count = event.generation_attempt_count + 1,
            claimed_at = ?,
            claimed_by = ?,
            published_at = NULL,
            updated_at = ?
        FROM candidates
        WHERE event.event_id = candidates.event_id
        RETURNING
          event.event_id,
          event.event_type,
          event.schema_version,
          event.tenant_id,
          event.aggregate_type,
          event.aggregate_id,
          event.aggregate_version,
          event.payload::text AS payload,
          event.occurred_at,
          event.publish_attempt_count,
          event.recovery_generation,
          event.generation_attempt_count,
          event.claimed_by,
          event.claimed_at,
          event.next_publish_at
      )
      SELECT
        event_id,
        event_type,
        schema_version,
        tenant_id,
        aggregate_type,
        aggregate_id,
        aggregate_version,
        payload,
        occurred_at,
        publish_attempt_count,
        recovery_generation,
        generation_attempt_count,
        claimed_by,
        claimed_at
      FROM claimed
      ORDER BY next_publish_at, occurred_at, event_id
      """;

  private final JdbcTemplate jdbcTemplate;
  private final OperationsIntegrationEventPayloadSerializer payloadSerializer;
  private final Clock clock;

  OperationsOutboxPersistenceAdapter(
      JdbcTemplate jdbcTemplate, JsonMapper jsonMapper, Clock clock) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.payloadSerializer = new OperationsIntegrationEventPayloadSerializer(jsonMapper);
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public OperationsIntegrationEvent append(PendingOperationsIntegrationEvent pendingEvent) {
    Objects.requireNonNull(pendingEvent, "Pending Operations event cannot be null");
    Instant now = clock.instant();
    if (now.isBefore(pendingEvent.occurredAt())) {
      throw new IllegalStateException(
          "Operations outbox clock cannot precede the event occurrence timestamp");
    }

    Long aggregateVersion =
        jdbcTemplate.queryForObject(
            ALLOCATE_VERSION_SQL,
            Long.class,
            pendingEvent.tenantId(),
            pendingEvent.aggregateType(),
            pendingEvent.aggregateId(),
            Timestamp.from(now),
            Timestamp.from(now));
    if (aggregateVersion == null) {
      throw new IllegalStateException("Operations aggregate version allocation returned null");
    }

    OperationsIntegrationEvent event =
        pendingEvent.materialize(UUID.randomUUID(), aggregateVersion);
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO operations_outbox_events (
              event_id,
              event_type,
              schema_version,
              tenant_id,
              aggregate_type,
              aggregate_id,
              aggregate_version,
              payload,
              occurred_at,
              publish_status,
              publish_attempt_count,
              next_publish_at,
              created_at,
              updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'PENDING', 0, ?, ?, ?)
            """,
            event.eventId(),
            event.eventType(),
            event.schemaVersion(),
            event.tenantId(),
            event.aggregateType(),
            event.aggregateId(),
            event.aggregateVersion(),
            payloadSerializer.serialize(event.payload()),
            Timestamp.from(event.occurredAt()),
            Timestamp.from(event.occurredAt()),
            Timestamp.from(now),
            Timestamp.from(now));
    if (inserted != 1) {
      throw new IllegalStateException("Operations outbox event was not inserted exactly once");
    }
    return event;
  }

  @Override
  @Transactional
  public List<ClaimedOperationsOutboxEvent> claimPublishable(OperationsOutboxClaim claim) {
    Objects.requireNonNull(claim, "Operations outbox claim cannot be null");
    return jdbcTemplate.query(
        CLAIM_SQL,
        OperationsOutboxPersistenceAdapter::mapClaimedEvent,
        Timestamp.from(claim.claimedAt()),
        Timestamp.from(claim.staleBefore()),
        claim.batchSize(),
        Timestamp.from(claim.claimedAt()),
        claim.claimOwner(),
        Timestamp.from(claim.claimedAt()));
  }

  @Override
  @Transactional
  public void markPublished(OperationsOutboxPublicationSuccess success) {
    Objects.requireNonNull(success, "Operations outbox publication success cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE operations_outbox_events
            SET publish_status = 'PUBLISHED',
                claimed_at = NULL,
                claimed_by = NULL,
                published_at = ?,
                last_failure_code = NULL,
                updated_at = ?
            WHERE event_id = ?
              AND publish_status = 'CLAIMED'
              AND claimed_by = ?
              AND publish_attempt_count = ?
            """,
            Timestamp.from(success.publishedAt()),
            Timestamp.from(success.publishedAt()),
            success.eventId(),
            success.claimOwner(),
            success.publicationAttempt());
    requireCurrentClaim(
        updated, success.eventId(), success.claimOwner(), success.publicationAttempt());
  }

  @Override
  @Transactional
  public void scheduleRetry(OperationsOutboxPublicationRetry retry) {
    Objects.requireNonNull(retry, "Operations outbox publication retry cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE operations_outbox_events
            SET publish_status = 'RETRY_SCHEDULED',
                next_publish_at = ?,
                claimed_at = NULL,
                claimed_by = NULL,
                published_at = NULL,
                last_failure_code = ?,
                updated_at = ?
            WHERE event_id = ?
              AND publish_status = 'CLAIMED'
              AND claimed_by = ?
              AND publish_attempt_count = ?
            """,
            Timestamp.from(retry.nextPublishAt()),
            retry.failureCode(),
            Timestamp.from(retry.recordedAt()),
            retry.eventId(),
            retry.claimOwner(),
            retry.publicationAttempt());
    requireCurrentClaim(updated, retry.eventId(), retry.claimOwner(), retry.publicationAttempt());
  }

  @Override
  @Transactional
  public void markFailed(OperationsOutboxPublicationFailure failure) {
    Objects.requireNonNull(failure, "Operations outbox publication failure cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE operations_outbox_events
            SET publish_status = 'FAILED',
                claimed_at = NULL,
                claimed_by = NULL,
                published_at = NULL,
                last_failure_code = ?,
                updated_at = ?
            WHERE event_id = ?
              AND publish_status = 'CLAIMED'
              AND claimed_by = ?
              AND publish_attempt_count = ?
            """,
            failure.failureCode(),
            Timestamp.from(failure.recordedAt()),
            failure.eventId(),
            failure.claimOwner(),
            failure.publicationAttempt());
    requireCurrentClaim(
        updated, failure.eventId(), failure.claimOwner(), failure.publicationAttempt());
  }

  private static ClaimedOperationsOutboxEvent mapClaimedEvent(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new ClaimedOperationsOutboxEvent(
        resultSet.getObject("event_id", UUID.class),
        resultSet.getString("event_type"),
        resultSet.getInt("schema_version"),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getString("aggregate_type"),
        resultSet.getObject("aggregate_id", UUID.class),
        resultSet.getLong("aggregate_version"),
        resultSet.getString("payload"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getInt("publish_attempt_count"),
        resultSet.getInt("recovery_generation"),
        resultSet.getInt("generation_attempt_count"),
        resultSet.getString("claimed_by"),
        resultSet.getTimestamp("claimed_at").toInstant());
  }

  private static void requireCurrentClaim(
      int updated, UUID eventId, String claimOwner, int publicationAttempt) {
    if (updated != 1) {
      throw new OperationsOutboxClaimLostException(eventId, claimOwner, publicationAttempt);
    }
  }
}
