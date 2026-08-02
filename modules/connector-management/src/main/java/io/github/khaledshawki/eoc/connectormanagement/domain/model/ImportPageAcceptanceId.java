package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ImportPageAcceptanceId(UUID value) {

  public ImportPageAcceptanceId {
    Objects.requireNonNull(value, "Import page acceptance id cannot be null");
  }

  public static ImportPageAcceptanceId of(UUID value) {
    return new ImportPageAcceptanceId(value);
  }
}
