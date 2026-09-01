package io.github.khaledshawki.eoc.platform.observability.metrics;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

final class OutboxBacklogMetricsSampler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxBacklogMetricsSampler.class);

  private static final String OPERATIONS_SQL =
      """
      SELECT COUNT(*) AS pending_count, MIN(occurred_at) AS oldest_occurred_at
      FROM operations_outbox_events
      WHERE publish_status IN ('PENDING', 'RETRY_SCHEDULED', 'CLAIMED')
      """;

  private static final String CONNECTOR_SQL =
      """
      SELECT COUNT(*) AS pending_count, MIN(occurred_at) AS oldest_occurred_at
      FROM connector_outbox_events
      WHERE publish_status IN ('PENDING', 'RETRY_SCHEDULED', 'CLAIMED')
      """;

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final EventPipelineMetrics metrics;

  OutboxBacklogMetricsSampler(
      JdbcTemplate jdbcTemplate, Clock clock, EventPipelineMetrics metrics) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    this.metrics = Objects.requireNonNull(metrics, "Event pipeline metrics cannot be null");
  }

  @Scheduled(
      initialDelayString = "${eoc.metrics.outbox.initial-delay-ms:5000}",
      fixedDelayString = "${eoc.metrics.outbox.fixed-delay-ms:5000}")
  void sample() {
    sample(EventPipeline.OPERATIONS, OPERATIONS_SQL);
    sample(EventPipeline.CONNECTOR, CONNECTOR_SQL);
  }

  private void sample(EventPipeline pipeline, String sql) {
    try {
      PendingSnapshot snapshot =
          jdbcTemplate.queryForObject(
              sql,
              (resultSet, rowNumber) ->
                  new PendingSnapshot(
                      resultSet.getLong("pending_count"),
                      resultSet.getTimestamp("oldest_occurred_at")));
      if (snapshot == null) {
        return;
      }
      metrics.updateOutboxBacklog(
          pipeline,
          snapshot.pending(),
          oldestSeconds(snapshot.oldestOccurredAt(), clock.instant()));
    } catch (DataAccessException exception) {
      LOGGER.warn("Unable to sample {} outbox backlog metrics", pipeline.tagValue(), exception);
    }
  }

  private static long oldestSeconds(Timestamp oldestOccurredAt, Instant now) {
    if (oldestOccurredAt == null) {
      return 0;
    }
    return Math.max(0, Duration.between(oldestOccurredAt.toInstant(), now).toSeconds());
  }

  private record PendingSnapshot(long pending, Timestamp oldestOccurredAt) {
    private PendingSnapshot {
      if (pending < 0) {
        throw new IllegalArgumentException("Pending outbox count cannot be negative");
      }
    }
  }
}
