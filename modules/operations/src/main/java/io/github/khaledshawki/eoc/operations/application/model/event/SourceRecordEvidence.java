package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SourceRecordEvidence(
    UUID sourceSystemId,
    String sourceIdentityKind,
    String sourceIdentity,
    String sourceVersion,
    Optional<Instant> sourceModifiedAt) {

  public SourceRecordEvidence {
    Objects.requireNonNull(sourceSystemId, "Event source system id cannot be null");
    SourceRecordIdentity.Kind kind = requireIdentityKind(sourceIdentityKind);
    SourceRecordIdentity normalizedIdentity = new SourceRecordIdentity(kind, sourceIdentity);
    SourceRecordVersion normalizedVersion = new SourceRecordVersion(sourceVersion);
    sourceIdentityKind = kind.name();
    sourceIdentity = normalizedIdentity.value();
    sourceVersion = normalizedVersion.value();
    sourceModifiedAt =
        Objects.requireNonNull(sourceModifiedAt, "Event source timestamp cannot be null");
  }

  public static SourceRecordEvidence from(
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt) {
    Objects.requireNonNull(sourceSystemId, "Source system id cannot be null");
    Objects.requireNonNull(sourceIdentity, "Source identity cannot be null");
    Objects.requireNonNull(sourceVersion, "Source version cannot be null");
    return new SourceRecordEvidence(
        sourceSystemId.value(),
        sourceIdentity.kind().name(),
        sourceIdentity.value(),
        sourceVersion.value(),
        sourceModifiedAt);
  }

  private static SourceRecordIdentity.Kind requireIdentityKind(String value) {
    Objects.requireNonNull(value, "Event source identity kind cannot be null");
    try {
      return SourceRecordIdentity.Kind.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Event source identity kind is not supported", exception);
    }
  }
}
