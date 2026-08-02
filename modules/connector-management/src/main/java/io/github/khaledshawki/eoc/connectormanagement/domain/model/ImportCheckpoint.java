package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;

/** Last source position whose records were durably accepted by the downstream workflow. */
public record ImportCheckpoint(
    ConnectorTenantId tenantId,
    ConnectorId connectorId,
    ImportType importType,
    ImportCursor cursor) {

  public ImportCheckpoint {
    Objects.requireNonNull(tenantId, "Import checkpoint tenant id cannot be null");
    Objects.requireNonNull(connectorId, "Import checkpoint connector id cannot be null");
    Objects.requireNonNull(importType, "Import checkpoint type cannot be null");
    Objects.requireNonNull(cursor, "Import checkpoint cursor cannot be null");
  }
}
