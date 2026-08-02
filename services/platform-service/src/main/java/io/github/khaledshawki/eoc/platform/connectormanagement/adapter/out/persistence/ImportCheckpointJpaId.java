package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

record ImportCheckpointJpaId(UUID tenantId, UUID connectorId, String importType)
    implements Serializable {

  ImportCheckpointJpaId {
    Objects.requireNonNull(tenantId, "Checkpoint tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Checkpoint connector id cannot be null");
    Objects.requireNonNull(importType, "Checkpoint import type cannot be null");
  }
}
