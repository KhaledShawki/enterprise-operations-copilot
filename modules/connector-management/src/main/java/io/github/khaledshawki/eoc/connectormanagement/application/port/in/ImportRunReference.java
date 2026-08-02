package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ImportRunReference(UUID tenantId, UUID importRunId) {

  public ImportRunReference {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
  }
}
