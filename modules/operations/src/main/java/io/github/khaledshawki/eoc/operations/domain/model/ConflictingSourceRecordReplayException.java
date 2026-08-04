package io.github.khaledshawki.eoc.operations.domain.model;

public final class ConflictingSourceRecordReplayException extends RuntimeException {

  public ConflictingSourceRecordReplayException(
      SourceRecordIdentity identity, SourceRecordVersion sourceVersion, String reason) {
    super(
        "Conflicting replay for source record "
            + identity.kind()
            + ":"
            + identity.value()
            + " at version "
            + sourceVersion.value()
            + ": "
            + reason);
  }
}
