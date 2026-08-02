package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Objects;
import java.util.Optional;

/**
 * Position and maximum size for one source retrieval. An incremental cursor starts a new scan,
 * while a page token continues an existing scan; they are therefore mutually exclusive.
 */
public record SourceFetchRequest(
    int pageSize,
    Optional<SourcePageToken> pageToken,
    Optional<IncrementalCursor> incrementalCursor) {

  public static final int MAX_PAGE_SIZE = 500;

  public SourceFetchRequest {
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("Source page size must be between 1 and " + MAX_PAGE_SIZE);
    }
    Objects.requireNonNull(pageToken, "Source page token cannot be null");
    Objects.requireNonNull(incrementalCursor, "Incremental cursor cannot be null");
    if (pageToken.isPresent() && incrementalCursor.isPresent()) {
      throw new IllegalArgumentException(
          "A source fetch request cannot combine a page token and an incremental cursor");
    }
  }

  public static SourceFetchRequest initial(int pageSize) {
    return new SourceFetchRequest(pageSize, Optional.empty(), Optional.empty());
  }

  public static SourceFetchRequest after(int pageSize, IncrementalCursor incrementalCursor) {
    return new SourceFetchRequest(pageSize, Optional.empty(), Optional.of(incrementalCursor));
  }

  public static SourceFetchRequest continueWith(int pageSize, SourcePageToken pageToken) {
    return new SourceFetchRequest(pageSize, Optional.of(pageToken), Optional.empty());
  }
}
