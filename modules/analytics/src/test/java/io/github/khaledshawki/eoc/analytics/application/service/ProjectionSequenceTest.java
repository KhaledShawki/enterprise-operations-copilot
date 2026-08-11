package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionRegressionException;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectionSequenceTest {

  private static final UUID AGGREGATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000098");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-12T00:00:00Z");

  @Test
  void acceptsVersionOneAsInitialProjection() {
    assertDoesNotThrow(
        () ->
            ProjectionSequence.requireInitial(
                cursor(EVENT_ID, 1, OCCURRED_AT), "INVOICE", AGGREGATE_ID));
  }

  @Test
  void rejectsNonInitialVersionForMissingProjection() {
    AnalyticsProjectionVersionGapException exception =
        assertThrows(
            AnalyticsProjectionVersionGapException.class,
            () ->
                ProjectionSequence.requireInitial(
                    cursor(EVENT_ID, 2, OCCURRED_AT), "INVOICE", AGGREGATE_ID));

    assertEquals(1, exception.expectedVersion());
    assertEquals(2, exception.actualVersion());
  }

  @Test
  void exactCurrentCursorIsSameVersion() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    assertEquals(
        ProjectionSequence.SequenceDecision.SAME_VERSION,
        ProjectionSequence.evaluate(current, current, "INVOICE", AGGREGATE_ID));
  }

  @Test
  void sameVersionWithDifferentEventIdConflicts() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () ->
            ProjectionSequence.evaluate(
                current, cursor(UUID.randomUUID(), 3, OCCURRED_AT), "INVOICE", AGGREGATE_ID));
  }

  @Test
  void sameVersionAndEventIdWithDifferentTimestampConflicts() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () ->
            ProjectionSequence.evaluate(
                current, cursor(EVENT_ID, 3, OCCURRED_AT.plusSeconds(1)), "INVOICE", AGGREGATE_ID));
  }

  @Test
  void acceptsExactlyNextVersion() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);
    ProjectionCursor incoming = cursor(UUID.randomUUID(), 4, OCCURRED_AT.plusSeconds(1));

    assertEquals(
        ProjectionSequence.SequenceDecision.NEXT_VERSION,
        ProjectionSequence.evaluate(current, incoming, "INVOICE", AGGREGATE_ID));
  }

  @Test
  void rejectsVersionGap() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    AnalyticsProjectionVersionGapException exception =
        assertThrows(
            AnalyticsProjectionVersionGapException.class,
            () ->
                ProjectionSequence.evaluate(
                    current,
                    cursor(UUID.randomUUID(), 5, OCCURRED_AT.plusSeconds(2)),
                    "INVOICE",
                    AGGREGATE_ID));

    assertEquals(4, exception.expectedVersion());
    assertEquals(5, exception.actualVersion());
  }

  @Test
  void rejectsOlderVersion() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    assertThrows(
        AnalyticsProjectionVersionRegressionException.class,
        () ->
            ProjectionSequence.evaluate(
                current,
                cursor(UUID.randomUUID(), 2, OCCURRED_AT.minusSeconds(1)),
                "INVOICE",
                AGGREGATE_ID));
  }

  @Test
  void rejectsEventIdReuseAcrossVersions() {
    ProjectionCursor current = cursor(EVENT_ID, 3, OCCURRED_AT);

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () ->
            ProjectionSequence.evaluate(
                current, cursor(EVENT_ID, 4, OCCURRED_AT.plusSeconds(1)), "INVOICE", AGGREGATE_ID));
  }

  private static ProjectionCursor cursor(UUID eventId, long version, Instant occurredAt) {
    return new ProjectionCursor(eventId, version, occurredAt);
  }
}
