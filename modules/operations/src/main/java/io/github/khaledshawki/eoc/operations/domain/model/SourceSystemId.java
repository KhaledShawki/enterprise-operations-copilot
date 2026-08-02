package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier of the source system that supplied an operational record. */
public record SourceSystemId(UUID value) {

  public SourceSystemId {
    Objects.requireNonNull(value, "Source system id cannot be null");
  }

  public static SourceSystemId of(UUID value) {
    return new SourceSystemId(value);
  }
}
