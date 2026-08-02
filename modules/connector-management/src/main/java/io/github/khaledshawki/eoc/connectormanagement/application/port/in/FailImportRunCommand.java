package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import java.util.Objects;
import java.util.UUID;

public record FailImportRunCommand(UUID tenantId, UUID importRunId, ImportFailure failure) {

  public FailImportRunCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(failure, "Import failure cannot be null");
  }
}
