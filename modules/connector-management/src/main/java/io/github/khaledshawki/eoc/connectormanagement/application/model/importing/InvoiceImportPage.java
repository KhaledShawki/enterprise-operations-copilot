package io.github.khaledshawki.eoc.connectormanagement.application.model.importing;

import io.github.khaledshawki.eoc.connectormanagement.application.model.datasource.SourceInvoiceRecord;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Connector-owned contract for one normalized invoice page sent downstream. */
public record InvoiceImportPage(
    UUID tenantId,
    UUID sourceSystemId,
    UUID importRunId,
    UUID pageAcceptanceId,
    List<SourceInvoiceRecord> records) {

  public InvoiceImportPage {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(sourceSystemId, "Source system id cannot be null");
    Objects.requireNonNull(importRunId, "Import run id cannot be null");
    Objects.requireNonNull(pageAcceptanceId, "Page acceptance id cannot be null");
    Objects.requireNonNull(records, "Invoice import records cannot be null");
    if (records.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Invoice import records cannot contain null");
    }
    records = List.copyOf(records);
  }
}
