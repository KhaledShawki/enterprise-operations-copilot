package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceFetchRequest;
import java.util.Objects;
import java.util.UUID;

/** Executes one bounded attempt for an existing connector import run. */
public record ExecuteImportRunCommand(UUID tenantId, UUID importRunId, int pageSize) {

  public ExecuteImportRunCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    if (pageSize < 1 || pageSize > SourceFetchRequest.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException(
          "Import page size must be between 1 and " + SourceFetchRequest.MAX_PAGE_SIZE);
    }
  }
}
