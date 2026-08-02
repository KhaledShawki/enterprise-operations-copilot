package io.github.khaledshawki.eoc.operations.domain.model;

public final class ConflictingSourceRecordVersionException extends RuntimeException {

  public ConflictingSourceRecordVersionException(SourceRecordIdentity identity, String reason) {
    super("Cannot order source record " + identity.kind() + ":" + identity.value() + ": " + reason);
  }
}
