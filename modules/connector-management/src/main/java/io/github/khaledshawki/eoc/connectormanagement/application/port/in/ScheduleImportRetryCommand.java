package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportFailure;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ScheduleImportRetryCommand(
    UUID tenantId, UUID importRunId, ImportFailure failure, Instant nextRetryAt) {

  public ScheduleImportRetryCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(failure, "Import failure cannot be null");
    Objects.requireNonNull(nextRetryAt, "Next retry timestamp cannot be null");
  }
}
