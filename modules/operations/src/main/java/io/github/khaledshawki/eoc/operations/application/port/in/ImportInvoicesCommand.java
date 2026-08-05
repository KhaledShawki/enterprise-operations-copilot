package io.github.khaledshawki.eoc.operations.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ImportInvoicesCommand(
    UUID tenantId,
    UUID sourceSystemId,
    UUID importBatchId,
    UUID pageAcceptanceId,
    List<InvoiceImportRecord> records) {

  public static final int MAX_RECORDS_PER_PAGE = 1_000;

  public ImportInvoicesCommand {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Source system id cannot be null");
    Objects.requireNonNull(importBatchId, "Import batch id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
    Objects.requireNonNull(records, "Invoice import records cannot be null");
    if (records.size() > MAX_RECORDS_PER_PAGE) {
      throw new IllegalArgumentException(
          "Invoice import page cannot exceed " + MAX_RECORDS_PER_PAGE + " records");
    }
    if (records.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice import records cannot contain null");
    }
    records = List.copyOf(records);
  }
}
