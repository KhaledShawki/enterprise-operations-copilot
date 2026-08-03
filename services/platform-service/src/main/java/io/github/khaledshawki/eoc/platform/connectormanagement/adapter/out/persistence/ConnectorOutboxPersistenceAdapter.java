package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorOutboxClaimLostException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationSuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorOutboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ConnectorOutboxPersistenceAdapter implements ConnectorOutboxRepository {

  private static final String CLAIM_SQL =
      """
      WITH candidates AS (
        SELECT event_id
        FROM connector_outbox_events
        WHERE (
          publish_status IN ('PENDING', 'RETRY_SCHEDULED')
          AND next_publish_at <= ?
        ) OR (
          publish_status = 'CLAIMED'
          AND claimed_at <= ?
        )
        ORDER BY next_publish_at, occurred_at, event_id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      ), claimed AS (
        UPDATE connector_outbox_events event
        SET publish_status = 'CLAIMED',
            publish_attempt_count = event.publish_attempt_count + 1,
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
          event.payload::text AS payload,
          event.occurred_at,
          event.publish_attempt_count,
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
        payload,
        occurred_at,
        publish_attempt_count,
        claimed_by,
        claimed_at
      FROM claimed
      ORDER BY next_publish_at, occurred_at, event_id
      """;

  private final JdbcTemplate jdbcTemplate;

  ConnectorOutboxPersistenceAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
  }

  @Override
  @Transactional
  public List<ClaimedConnectorOutboxEvent> claimPublishable(ConnectorOutboxClaim claim) {
    Objects.requireNonNull(claim, "Connector outbox claim cannot be null");
    return jdbcTemplate.query(
        CLAIM_SQL,
        ConnectorOutboxPersistenceAdapter::mapClaimedEvent,
        Timestamp.from(claim.claimedAt()),
        Timestamp.from(claim.staleBefore()),
        claim.batchSize(),
        Timestamp.from(claim.claimedAt()),
        claim.claimOwner(),
        Timestamp.from(claim.claimedAt()));
  }

  @Override
  @Transactional
  public void markPublished(ConnectorOutboxPublicationSuccess success) {
    Objects.requireNonNull(success, "Connector outbox publication success cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_outbox_events
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
  public void scheduleRetry(ConnectorOutboxPublicationRetry retry) {
    Objects.requireNonNull(retry, "Connector outbox publication retry cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_outbox_events
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
  public void markFailed(ConnectorOutboxPublicationFailure failure) {
    Objects.requireNonNull(failure, "Connector outbox publication failure cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_outbox_events
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

  private static ClaimedConnectorOutboxEvent mapClaimedEvent(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new ClaimedConnectorOutboxEvent(
        resultSet.getObject("event_id", java.util.UUID.class),
        resultSet.getString("event_type"),
        resultSet.getInt("schema_version"),
        resultSet.getObject("tenant_id", java.util.UUID.class),
        resultSet.getString("aggregate_type"),
        resultSet.getObject("aggregate_id", java.util.UUID.class),
        resultSet.getString("payload"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getInt("publish_attempt_count"),
        resultSet.getString("claimed_by"),
        resultSet.getTimestamp("claimed_at").toInstant());
  }

  private static void requireCurrentClaim(
      int updated, java.util.UUID eventId, String claimOwner, int publicationAttempt) {
    if (updated != 1) {
      throw new ConnectorOutboxClaimLostException(eventId, claimOwner, publicationAttempt);
    }
  }
}
