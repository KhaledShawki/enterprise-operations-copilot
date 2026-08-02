package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatistics;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RecordAcceptedImportPageCommand(
    UUID tenantId,
    UUID importRunId,
    UUID acceptanceId,
    Optional<String> expectedCursor,
    Optional<String> candidateCursor,
    ImportStatistics statistics) {

  public RecordAcceptedImportPageCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(acceptanceId, "Import page acceptance id cannot be null");
    Objects.requireNonNull(expectedCursor, "Expected cursor cannot be null");
    Objects.requireNonNull(candidateCursor, "Candidate cursor cannot be null");
    Objects.requireNonNull(statistics, "Import page statistics cannot be null");
  }
}
