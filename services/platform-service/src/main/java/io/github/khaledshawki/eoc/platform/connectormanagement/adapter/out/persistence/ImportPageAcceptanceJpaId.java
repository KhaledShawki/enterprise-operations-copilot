package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record ImportPageAcceptanceJpaId(UUID importRunId, UUID acceptanceId) implements Serializable {

  ImportPageAcceptanceJpaId {
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(acceptanceId, "Acceptance id cannot be null");
  }
}
