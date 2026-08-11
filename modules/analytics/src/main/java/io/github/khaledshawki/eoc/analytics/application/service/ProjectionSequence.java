package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionRegressionException;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.util.Objects;
import java.util.UUID;

final class ProjectionSequence {

  private ProjectionSequence() {}

  static void requireInitial(ProjectionCursor incoming, String aggregateType, UUID aggregateId) {
    Objects.requireNonNull(incoming, "Incoming projection cursor cannot be null");
    if (incoming.aggregateVersion() != 1) {
      throw new AnalyticsProjectionVersionGapException(
          aggregateType, aggregateId, 1, incoming.aggregateVersion());
    }
  }

  static SequenceDecision evaluate(
      ProjectionCursor current, ProjectionCursor incoming, String aggregateType, UUID aggregateId) {
    Objects.requireNonNull(current, "Current projection cursor cannot be null");
    Objects.requireNonNull(incoming, "Incoming projection cursor cannot be null");

    long currentVersion = current.aggregateVersion();
    long incomingVersion = incoming.aggregateVersion();

    if (incomingVersion < currentVersion) {
      throw new AnalyticsProjectionVersionRegressionException(
          aggregateType, aggregateId, currentVersion, incomingVersion);
    }

    if (incomingVersion == currentVersion) {
      if (!incoming.equals(current)) {
        throw new AnalyticsProjectionVersionConflictException(
            aggregateType,
            aggregateId,
            incomingVersion,
            "the same aggregate version has different event identity or occurrence time");
      }
      return SequenceDecision.SAME_VERSION;
    }

    if (incoming.eventId().equals(current.eventId())) {
      throw new AnalyticsProjectionVersionConflictException(
          aggregateType,
          aggregateId,
          incomingVersion,
          "the same event id cannot identify multiple aggregate versions");
    }

    long expectedVersion = Math.addExact(currentVersion, 1);
    if (incomingVersion != expectedVersion) {
      throw new AnalyticsProjectionVersionGapException(
          aggregateType, aggregateId, expectedVersion, incomingVersion);
    }
    return SequenceDecision.NEXT_VERSION;
  }

  enum SequenceDecision {
    SAME_VERSION,
    NEXT_VERSION
  }
}
