package io.github.khaledshawki.eoc.operations.application.port.in;

public interface ImportInvoicesUseCase {

  InvoiceImportResult importPage(ImportInvoicesCommand command);
}
