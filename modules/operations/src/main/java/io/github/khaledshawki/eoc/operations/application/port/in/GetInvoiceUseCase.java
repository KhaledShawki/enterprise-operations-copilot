package io.github.khaledshawki.eoc.operations.application.port.in;

public interface GetInvoiceUseCase {

  InvoiceResult get(GetInvoiceQuery query);
}
