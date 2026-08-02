package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Identity and modification evidence shared by every normalized source record. */
public record SourceRecordMetadata(
    SourceIdentity identity,
    SourceModificationVersion modificationVersion,
    Optional<Instant> sourceModifiedAt) {

  public SourceRecordMetadata {
    Objects.requireNonNull(identity, "Source identity cannot be null");
    Objects.requireNonNull(modificationVersion, "Source modification version cannot be null");
    Objects.requireNonNull(sourceModifiedAt, "Source modification timestamp cannot be null");
  }

  public static SourceRecordMetadata withoutModificationTimestamp(
      SourceIdentity identity, SourceModificationVersion modificationVersion) {
    return new SourceRecordMetadata(identity, modificationVersion, Optional.empty());
  }
}
