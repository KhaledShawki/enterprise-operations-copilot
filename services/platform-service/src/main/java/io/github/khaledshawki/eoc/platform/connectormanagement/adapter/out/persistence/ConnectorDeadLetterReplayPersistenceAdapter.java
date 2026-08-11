package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayClaimLostException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayCollisionException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterHeader;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayStatus;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
class ConnectorDeadLetterReplayPersistenceAdapter implements ConnectorDeadLetterReplayRepository {

  private static final String REQUEST_RESULT_COLUMNS =
      """
      replay_request_id,
      dlt_partition,
      dlt_offset,
      replay_status,
      replay_generation,
      requested_by_issuer,
      requested_by_subject,
      request_reason,
      requested_at,
      publication_attempt_count,
      last_failure_code,
      replayed_at
      """;

  private static final String CLAIM_SQL =
      """
      WITH candidates AS (
        SELECT replay_request_id
        FROM connector_dead_letter_replay_requests
        WHERE (
          replay_status IN ('PENDING', 'RETRY_SCHEDULED')
          AND next_attempt_at <= ?
        ) OR (
          replay_status = 'CLAIMED'
          AND claimed_at <= ?
        )
        ORDER BY next_attempt_at, requested_at, replay_request_id
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      ), claimed AS (
        UPDATE connector_dead_letter_replay_requests replay
        SET replay_status = 'CLAIMED',
            publication_attempt_count = replay.publication_attempt_count + 1,
            claimed_at = ?,
            claimed_by = ?,
            replayed_at = NULL,
            updated_at = ?
        FROM candidates
        WHERE replay.replay_request_id = candidates.replay_request_id
        RETURNING
          replay.replay_request_id,
          replay.dlt_partition,
          replay.dlt_offset,
          replay.source_topic,
          replay.source_partition,
          replay.source_offset,
          replay.source_timestamp,
          replay.record_key,
          replay.record_value,
          replay.replay_headers::text AS replay_headers,
          replay.replay_generation,
          replay.publication_attempt_count,
          replay.claimed_by,
          replay.claimed_at,
          replay.next_attempt_at,
          replay.requested_at
      )
      SELECT
        replay_request_id,
        dlt_partition,
        dlt_offset,
        source_topic,
        source_partition,
        source_offset,
        source_timestamp,
        record_key,
        record_value,
        replay_headers,
        replay_generation,
        publication_attempt_count,
        claimed_by,
        claimed_at
      FROM claimed
      ORDER BY next_attempt_at, requested_at, replay_request_id
      """;

  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;

  ConnectorDeadLetterReplayPersistenceAdapter(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
  }

  @Override
  @Transactional
  public ConnectorDeadLetterReplayRequest request(NewConnectorDeadLetterReplayRequest request) {
    Objects.requireNonNull(request, "New replay request cannot be null");
    String headers = serializeHeaders(request.deadLetter().replayHeaders());
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO connector_dead_letter_replay_requests (
              replay_request_id,
              dlt_topic,
              dlt_partition,
              dlt_offset,
              record_fingerprint,
              source_topic,
              source_partition,
              source_offset,
              source_timestamp,
              record_key,
              record_value,
              replay_headers,
              replay_generation,
              requested_by_issuer,
              requested_by_subject,
              request_reason,
              replay_status,
              publication_attempt_count,
              next_attempt_at,
              claimed_at,
              claimed_by,
              last_failure_code,
              requested_at,
              replayed_at,
              updated_at
            ) VALUES (
              ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?,
              'PENDING', 0, ?, NULL, NULL, NULL, ?, NULL, ?
            )
            ON CONFLICT (dlt_topic, dlt_partition, dlt_offset) DO NOTHING
            """,
            request.requestId(),
            request.deadLetter().deadLetterTopic(),
            request.deadLetter().reference().partition(),
            request.deadLetter().reference().offset(),
            request.recordFingerprint(),
            request.deadLetter().sourceTopic(),
            request.deadLetter().sourcePartition(),
            request.deadLetter().sourceOffset(),
            Timestamp.from(request.deadLetter().sourceTimestamp()),
            request.deadLetter().key().orElse(null),
            request.deadLetter().value().orElse(null),
            headers,
            request.replayGeneration(),
            request.requestedByIssuer(),
            request.requestedBySubject(),
            request.reason(),
            Timestamp.from(request.requestedAt()),
            Timestamp.from(request.requestedAt()),
            Timestamp.from(request.requestedAt()));

    if (inserted == 0) {
      ExistingRequest existing =
          findExisting(request.deadLetter().reference(), request.deadLetter().deadLetterTopic());
      if (!request.recordFingerprint().equals(existing.fingerprint())) {
        throw new ConnectorDeadLetterReplayCollisionException(request.deadLetter().reference());
      }
      return existing.request();
    }
    return findById(request.requestId()).orElseThrow();
  }

  @Override
  public Optional<ConnectorDeadLetterReplayRequest> findById(UUID requestId) {
    Objects.requireNonNull(requestId, "Replay request id cannot be null");
    return jdbcTemplate
        .query(
            "SELECT "
                + REQUEST_RESULT_COLUMNS
                + " FROM connector_dead_letter_replay_requests WHERE replay_request_id = ?",
            ConnectorDeadLetterReplayPersistenceAdapter::mapRequest,
            requestId)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional
  public List<ClaimedConnectorDeadLetterReplay> claimPublishable(
      ConnectorDeadLetterReplayClaim claim) {
    Objects.requireNonNull(claim, "Replay claim cannot be null");
    return jdbcTemplate.query(
        CLAIM_SQL,
        this::mapClaimed,
        Timestamp.from(claim.claimedAt()),
        Timestamp.from(claim.staleBefore()),
        claim.batchSize(),
        Timestamp.from(claim.claimedAt()),
        claim.workerId(),
        Timestamp.from(claim.claimedAt()));
  }

  @Override
  @Transactional
  public void markReplayed(ConnectorDeadLetterReplaySuccess success) {
    Objects.requireNonNull(success, "Replay success cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_dead_letter_replay_requests
            SET replay_status = 'REPLAYED',
                claimed_at = NULL,
                claimed_by = NULL,
                replayed_at = ?,
                last_failure_code = NULL,
                updated_at = ?
            WHERE replay_request_id = ?
              AND replay_status = 'CLAIMED'
              AND claimed_by = ?
              AND publication_attempt_count = ?
            """,
            Timestamp.from(success.replayedAt()),
            Timestamp.from(success.replayedAt()),
            success.requestId(),
            success.claimOwner(),
            success.publicationAttempt());
    requireCurrentClaim(
        updated, success.requestId(), success.claimOwner(), success.publicationAttempt());
  }

  @Override
  @Transactional
  public void scheduleRetry(ConnectorDeadLetterReplayRetry retry) {
    Objects.requireNonNull(retry, "Replay retry cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_dead_letter_replay_requests
            SET replay_status = 'RETRY_SCHEDULED',
                next_attempt_at = ?,
                claimed_at = NULL,
                claimed_by = NULL,
                replayed_at = NULL,
                last_failure_code = ?,
                updated_at = ?
            WHERE replay_request_id = ?
              AND replay_status = 'CLAIMED'
              AND claimed_by = ?
              AND publication_attempt_count = ?
            """,
            Timestamp.from(retry.nextAttemptAt()),
            retry.failureCode(),
            Timestamp.from(retry.recordedAt()),
            retry.requestId(),
            retry.claimOwner(),
            retry.publicationAttempt());
    requireCurrentClaim(updated, retry.requestId(), retry.claimOwner(), retry.publicationAttempt());
  }

  @Override
  @Transactional
  public void markFailed(ConnectorDeadLetterReplayFailure failure) {
    Objects.requireNonNull(failure, "Replay failure cannot be null");
    int updated =
        jdbcTemplate.update(
            """
            UPDATE connector_dead_letter_replay_requests
            SET replay_status = 'FAILED',
                claimed_at = NULL,
                claimed_by = NULL,
                replayed_at = NULL,
                last_failure_code = ?,
                updated_at = ?
            WHERE replay_request_id = ?
              AND replay_status = 'CLAIMED'
              AND claimed_by = ?
              AND publication_attempt_count = ?
            """,
            failure.failureCode(),
            Timestamp.from(failure.recordedAt()),
            failure.requestId(),
            failure.claimOwner(),
            failure.publicationAttempt());
    requireCurrentClaim(
        updated, failure.requestId(), failure.claimOwner(), failure.publicationAttempt());
  }

  private ExistingRequest findExisting(ConnectorDeadLetterReference reference, String topic) {
    return jdbcTemplate
        .query(
            "SELECT record_fingerprint, "
                + REQUEST_RESULT_COLUMNS
                + " FROM connector_dead_letter_replay_requests"
                + " WHERE dlt_topic = ? AND dlt_partition = ? AND dlt_offset = ?",
            (resultSet, rowNumber) ->
                new ExistingRequest(
                    resultSet.getString("record_fingerprint"), mapRequest(resultSet, rowNumber)),
            topic,
            reference.partition(),
            reference.offset())
        .stream()
        .findFirst()
        .orElseThrow();
  }

  private ClaimedConnectorDeadLetterReplay mapClaimed(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new ClaimedConnectorDeadLetterReplay(
        resultSet.getObject("replay_request_id", UUID.class),
        new ConnectorDeadLetterReference(
            resultSet.getInt("dlt_partition"), resultSet.getLong("dlt_offset")),
        resultSet.getString("source_topic"),
        resultSet.getInt("source_partition"),
        resultSet.getLong("source_offset"),
        resultSet.getTimestamp("source_timestamp").toInstant(),
        Optional.ofNullable(resultSet.getString("record_key")),
        Optional.ofNullable(resultSet.getString("record_value")),
        deserializeHeaders(resultSet.getString("replay_headers")),
        resultSet.getInt("replay_generation"),
        resultSet.getInt("publication_attempt_count"),
        resultSet.getString("claimed_by"),
        resultSet.getTimestamp("claimed_at").toInstant());
  }

  private static ConnectorDeadLetterReplayRequest mapRequest(ResultSet resultSet, int rowNumber)
      throws SQLException {
    Timestamp replayedAt = resultSet.getTimestamp("replayed_at");
    return new ConnectorDeadLetterReplayRequest(
        resultSet.getObject("replay_request_id", UUID.class),
        new ConnectorDeadLetterReference(
            resultSet.getInt("dlt_partition"), resultSet.getLong("dlt_offset")),
        ConnectorDeadLetterReplayStatus.valueOf(resultSet.getString("replay_status")),
        resultSet.getInt("replay_generation"),
        resultSet.getString("requested_by_issuer"),
        resultSet.getString("requested_by_subject"),
        resultSet.getString("request_reason"),
        resultSet.getTimestamp("requested_at").toInstant(),
        resultSet.getInt("publication_attempt_count"),
        Optional.ofNullable(resultSet.getString("last_failure_code")),
        replayedAt == null ? Optional.empty() : Optional.of(replayedAt.toInstant()));
  }

  private String serializeHeaders(List<ConnectorDeadLetterHeader> headers) {
    try {
      return jsonMapper.writeValueAsString(headers);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Connector replay headers cannot be serialized", exception);
    }
  }

  private List<ConnectorDeadLetterHeader> deserializeHeaders(String value) {
    try {
      ConnectorDeadLetterHeader[] headers =
          jsonMapper.readValue(value, ConnectorDeadLetterHeader[].class);
      return List.copyOf(Arrays.asList(headers));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored connector replay headers are invalid", exception);
    }
  }

  private static void requireCurrentClaim(
      int updated, UUID requestId, String claimOwner, int publicationAttempt) {
    if (updated != 1) {
      throw new ConnectorDeadLetterReplayClaimLostException(
          requestId, claimOwner, publicationAttempt);
    }
  }

  private record ExistingRequest(String fingerprint, ConnectorDeadLetterReplayRequest request) {}
}
