package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable page of normalized source records. The candidate cursor is not a committed
 * checkpoint; import orchestration may persist it only after the page has been durably accepted.
 */
public record SourcePage<T>(
    List<T> records,
    Optional<SourcePageToken> nextPageToken,
    Optional<IncrementalCursor> candidateCursor) {

  public SourcePage {
    Objects.requireNonNull(records, "Source records cannot be null");
    if (records.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Source records cannot contain null values");
    }
    records = List.copyOf(records);
    Objects.requireNonNull(nextPageToken, "Next source page token cannot be null");
    Objects.requireNonNull(candidateCursor, "Candidate incremental cursor cannot be null");
  }
}
