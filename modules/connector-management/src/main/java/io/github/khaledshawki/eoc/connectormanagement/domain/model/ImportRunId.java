package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ImportRunId(UUID value) {

  public ImportRunId {
    Objects.requireNonNull(value, "Import run id cannot be null");
  }

  public static ImportRunId of(UUID value) {
    return new ImportRunId(value);
  }

  public static ImportRunId generate() {
    return new ImportRunId(UUID.randomUUID());
  }
}
