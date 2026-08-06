package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportOutcome;
import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.InvoiceImportPage;

/** Downstream boundary for durably accepting a normalized invoice page. */
public interface InvoiceImportPort {

  InvoiceImportOutcome importPage(InvoiceImportPage page);
}
