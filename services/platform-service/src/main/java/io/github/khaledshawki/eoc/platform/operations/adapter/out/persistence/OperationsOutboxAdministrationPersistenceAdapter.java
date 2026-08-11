package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxEventNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxRecoveryConflictException;
import io.github.khaledshawki.eoc.operations.application.model.outbox.NewOperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxCursor;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecoveryPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxStatus;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxInspectionRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OperationsOutboxAdministrationPersistenceAdapter
    implements OperationsOutboxInspectionRepository, OperationsOutboxRecoveryRepository {

  private static final String EVENT_COLUMNS =
      """
      event_id,
      event_type,
      schema_version,
      tenant_id,
      aggregate_type,
      aggregate_id,
      aggregate_version,
      occurred_at,
      publish_status,
      publish_attempt_count,
      recovery_generation,
      generation_attempt_count,
      next_publish_at,
      claimed_at,
      claimed_by,
      published_at,
      last_failure_code,
      created_at,
      updated_at
      """;

  private final JdbcTemplate jdbcTemplate;

  OperationsOutboxAdministrationPersistenceAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public OperationsOutboxPage list(OperationsOutboxInspectionFilter filter) {
    Objects.requireNonNull(filter, "Operations outbox inspection filter cannot be null");
    StringBuilder sql =
        new StringBuilder("SELECT ")
            .append(EVENT_COLUMNS)
            .append(" FROM operations_outbox_events WHERE 1 = 1");
    List<Object> arguments = new ArrayList<>();

    filter
        .status()
        .ifPresent(status -> add(sql, arguments, " AND publish_status = ?", status.name()));
    filter.tenantId().ifPresent(tenantId -> add(sql, arguments, " AND tenant_id = ?", tenantId));
    filter.aggregateType().ifPresent(type -> add(sql, arguments, " AND aggregate_type = ?", type));
    filter.aggregateId().ifPresent(id -> add(sql, arguments, " AND aggregate_id = ?", id));
    filter
        .cursor()
        .ifPresent(
            cursor -> {
              sql.append(" AND (created_at < ? OR (created_at = ? AND event_id < ?))");
              arguments.add(Timestamp.from(cursor.createdAt()));
              arguments.add(Timestamp.from(cursor.createdAt()));
              arguments.add(cursor.eventId());
            });
    sql.append(" ORDER BY created_at DESC, event_id DESC LIMIT ?");
    arguments.add(filter.limit() + 1);

    List<OperationsOutboxEventView> rows =
        jdbcTemplate.query(
            sql.toString(),
            OperationsOutboxAdministrationPersistenceAdapter::mapEvent,
            arguments.toArray());
    boolean hasMore = rows.size() > filter.limit();
    List<OperationsOutboxEventView> events =
        hasMore ? List.copyOf(rows.subList(0, filter.limit())) : List.copyOf(rows);
    Optional<OperationsOutboxCursor> nextCursor =
        hasMore
            ? Optional.of(
                new OperationsOutboxCursor(
                    events.getLast().createdAt(), events.getLast().eventId()))
            : Optional.empty();
    return new OperationsOutboxPage(events, nextCursor);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OperationsOutboxEventView> findById(UUID eventId) {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    return jdbcTemplate
        .query(
            "SELECT " + EVENT_COLUMNS + " FROM operations_outbox_events WHERE event_id = ?",
            OperationsOutboxAdministrationPersistenceAdapter::mapEvent,
            eventId)
        .stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public OperationsOutboxRecoveryPage listRecoveries(
      UUID eventId, Optional<Integer> beforeGeneration, int limit) {
    Objects.requireNonNull(eventId, "Operations outbox recovery event id cannot be null");
    Objects.requireNonNull(beforeGeneration, "Operations outbox recovery cursor cannot be null");
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              recovery_id,
              event_id,
              recovery_generation,
              requested_by_issuer,
              requested_by_subject,
              reason,
              previous_status,
              previous_publish_attempt_count,
              previous_generation_attempt_count,
              previous_failure_code,
              requested_at,
              completed_at
            FROM operations_outbox_recoveries
            WHERE event_id = ?
            """);
    List<Object> arguments = new ArrayList<>();
    arguments.add(eventId);
    beforeGeneration.ifPresent(
        generation -> {
          sql.append(" AND recovery_generation < ?");
          arguments.add(generation);
        });
    sql.append(" ORDER BY recovery_generation DESC LIMIT ?");
    arguments.add(limit + 1);

    List<OperationsOutboxRecovery> rows =
        jdbcTemplate.query(
            sql.toString(),
            OperationsOutboxAdministrationPersistenceAdapter::mapRecovery,
            arguments.toArray());
    boolean hasMore = rows.size() > limit;
    List<OperationsOutboxRecovery> recoveries =
        hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
    Optional<Integer> nextBeforeGeneration =
        hasMore ? Optional.of(recoveries.getLast().recoveryGeneration()) : Optional.empty();
    return new OperationsOutboxRecoveryPage(recoveries, nextBeforeGeneration);
  }

  @Override
  @Transactional
  public OperationsOutboxRecovery recover(NewOperationsOutboxRecovery recovery) {
    Objects.requireNonNull(recovery, "Operations outbox recovery cannot be null");
    RecoveryCandidate candidate = loadRecoveryCandidate(recovery.eventId());
    if (candidate.status() != OperationsOutboxStatus.FAILED) {
      throw new OperationsOutboxRecoveryConflictException(
          recovery.eventId(),
          "Operations outbox event is not recoverable from status " + candidate.status());
    }
    if (hasUnpublishedPredecessor(candidate)) {
      throw new OperationsOutboxRecoveryConflictException(
          recovery.eventId(), "Operations outbox event has an unpublished aggregate predecessor");
    }
    if (recovery.requestedAt().isBefore(candidate.updatedAt())) {
      throw new IllegalStateException(
          "Operations outbox recovery timestamp cannot precede the current event state");
    }

    int nextGeneration = Math.addExact(candidate.recoveryGeneration(), 1);
    Instant completedAt = recovery.requestedAt();
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO operations_outbox_recoveries (
              recovery_id,
              event_id,
              recovery_generation,
              requested_by_issuer,
              requested_by_subject,
              reason,
              previous_status,
              previous_publish_attempt_count,
              previous_generation_attempt_count,
              previous_failure_code,
              requested_at,
              completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'FAILED', ?, ?, ?, ?, ?)
            """,
            recovery.recoveryId(),
            recovery.eventId(),
            nextGeneration,
            recovery.actor().issuer(),
            recovery.actor().subject(),
            recovery.reason(),
            candidate.publicationAttemptCount(),
            candidate.generationAttemptCount(),
            candidate.lastFailureCode(),
            Timestamp.from(recovery.requestedAt()),
            Timestamp.from(completedAt));
    if (inserted != 1) {
      throw new IllegalStateException(
          "Operations outbox recovery audit was not inserted exactly once");
    }

    int updated =
        jdbcTemplate.update(
            """
            UPDATE operations_outbox_events
            SET publish_status = 'RETRY_SCHEDULED',
                recovery_generation = ?,
                generation_attempt_count = 0,
                next_publish_at = ?,
                claimed_at = NULL,
                claimed_by = NULL,
                published_at = NULL,
                updated_at = ?
            WHERE event_id = ?
              AND publish_status = 'FAILED'
              AND recovery_generation = ?
              AND publish_attempt_count = ?
              AND generation_attempt_count = ?
            """,
            nextGeneration,
            Timestamp.from(completedAt),
            Timestamp.from(completedAt),
            recovery.eventId(),
            candidate.recoveryGeneration(),
            candidate.publicationAttemptCount(),
            candidate.generationAttemptCount());
    if (updated != 1) {
      throw new OperationsOutboxRecoveryConflictException(
          recovery.eventId(), "Operations outbox event changed while recovery was being applied");
    }

    return new OperationsOutboxRecovery(
        recovery.recoveryId(),
        recovery.eventId(),
        nextGeneration,
        recovery.actor().issuer(),
        recovery.actor().subject(),
        recovery.reason(),
        OperationsOutboxStatus.FAILED,
        candidate.publicationAttemptCount(),
        candidate.generationAttemptCount(),
        candidate.lastFailureCode(),
        recovery.requestedAt(),
        completedAt);
  }

  private RecoveryCandidate loadRecoveryCandidate(UUID eventId) {
    List<RecoveryCandidate> candidates =
        jdbcTemplate.query(
            """
            SELECT
              event_id,
              tenant_id,
              aggregate_type,
              aggregate_id,
              aggregate_version,
              publish_status,
              publish_attempt_count,
              recovery_generation,
              generation_attempt_count,
              last_failure_code,
              updated_at
            FROM operations_outbox_events
            WHERE event_id = ?
            FOR UPDATE
            """,
            (resultSet, rowNumber) ->
                new RecoveryCandidate(
                    resultSet.getObject("event_id", UUID.class),
                    resultSet.getObject("tenant_id", UUID.class),
                    resultSet.getString("aggregate_type"),
                    resultSet.getObject("aggregate_id", UUID.class),
                    resultSet.getLong("aggregate_version"),
                    OperationsOutboxStatus.valueOf(resultSet.getString("publish_status")),
                    resultSet.getInt("publish_attempt_count"),
                    resultSet.getInt("recovery_generation"),
                    resultSet.getInt("generation_attempt_count"),
                    resultSet.getString("last_failure_code"),
                    resultSet.getTimestamp("updated_at").toInstant()),
            eventId);
    if (candidates.isEmpty()) {
      throw new OperationsOutboxEventNotFoundException(eventId);
    }
    if (candidates.size() != 1) {
      throw new IllegalStateException("Operations outbox event identity is not unique");
    }
    return candidates.getFirst();
  }

  private boolean hasUnpublishedPredecessor(RecoveryCandidate candidate) {
    Boolean blocked =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM operations_outbox_events predecessor
              WHERE predecessor.tenant_id = ?
                AND predecessor.aggregate_type = ?
                AND predecessor.aggregate_id = ?
                AND predecessor.aggregate_version < ?
                AND predecessor.publish_status <> 'PUBLISHED'
            )
            """,
            Boolean.class,
            candidate.tenantId(),
            candidate.aggregateType(),
            candidate.aggregateId(),
            candidate.aggregateVersion());
    return Boolean.TRUE.equals(blocked);
  }

  private static void add(
      StringBuilder sql, List<Object> arguments, String predicate, Object argument) {
    sql.append(predicate);
    arguments.add(argument);
  }

  private static OperationsOutboxEventView mapEvent(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new OperationsOutboxEventView(
        resultSet.getObject("event_id", UUID.class),
        resultSet.getString("event_type"),
        resultSet.getInt("schema_version"),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getString("aggregate_type"),
        resultSet.getObject("aggregate_id", UUID.class),
        resultSet.getLong("aggregate_version"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        OperationsOutboxStatus.valueOf(resultSet.getString("publish_status")),
        resultSet.getInt("publish_attempt_count"),
        resultSet.getInt("recovery_generation"),
        resultSet.getInt("generation_attempt_count"),
        resultSet.getTimestamp("next_publish_at").toInstant(),
        optionalInstant(resultSet, "claimed_at"),
        Optional.ofNullable(resultSet.getString("claimed_by")),
        optionalInstant(resultSet, "published_at"),
        Optional.ofNullable(resultSet.getString("last_failure_code")),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private static OperationsOutboxRecovery mapRecovery(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new OperationsOutboxRecovery(
        resultSet.getObject("recovery_id", UUID.class),
        resultSet.getObject("event_id", UUID.class),
        resultSet.getInt("recovery_generation"),
        resultSet.getString("requested_by_issuer"),
        resultSet.getString("requested_by_subject"),
        resultSet.getString("reason"),
        OperationsOutboxStatus.valueOf(resultSet.getString("previous_status")),
        resultSet.getInt("previous_publish_attempt_count"),
        resultSet.getInt("previous_generation_attempt_count"),
        resultSet.getString("previous_failure_code"),
        resultSet.getTimestamp("requested_at").toInstant(),
        resultSet.getTimestamp("completed_at").toInstant());
  }

  private static Optional<Instant> optionalInstant(ResultSet resultSet, String column)
      throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
  }

  private record RecoveryCandidate(
      UUID eventId,
      UUID tenantId,
      String aggregateType,
      UUID aggregateId,
      long aggregateVersion,
      OperationsOutboxStatus status,
      int publicationAttemptCount,
      int recoveryGeneration,
      int generationAttemptCount,
      String lastFailureCode,
      Instant updatedAt) {}
}
