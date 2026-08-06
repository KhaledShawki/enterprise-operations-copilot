package io.github.khaledshawki.eoc.operations.application.port.in;

public interface ListInvoicesUseCase {

  InvoicePageResult list(ListInvoicesQuery query);
}
