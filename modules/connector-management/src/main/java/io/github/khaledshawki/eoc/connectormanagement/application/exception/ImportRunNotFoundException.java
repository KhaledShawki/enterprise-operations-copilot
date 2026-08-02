package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportRunId;
import java.util.Objects;

public final class ImportRunNotFoundException extends RuntimeException {

  public ImportRunNotFoundException(ConnectorTenantId tenantId, ImportRunId importRunId) {
    super(message(tenantId, importRunId));
  }

  private static String message(ConnectorTenantId tenantId, ImportRunId importRunId) {
    Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    return "Import run %s was not found for tenant %s"
        .formatted(importRunId.value(), tenantId.value());
  }
}
