package io.github.khaledshawki.eoc.operations.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ImportBusinessPartnersCommand(
    UUID tenantId,
    UUID sourceSystemId,
    UUID importBatchId,
    UUID pageAcceptanceId,
    List<BusinessPartnerImportRecord> records) {

  public static final int MAX_RECORDS_PER_PAGE = 1_000;

  public ImportBusinessPartnersCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Import batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
    Objects.requireNonNull(records, "Business partner import records cannot be null");
    if (records.size() > MAX_RECORDS_PER_PAGE) {
      throw new IllegalArgumentException(
          "Business partner import page cannot exceed " + MAX_RECORDS_PER_PAGE + " records");
    }
    if (records.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Business partner import records cannot contain null");
    }
    records = List.copyOf(records);
  }
}
