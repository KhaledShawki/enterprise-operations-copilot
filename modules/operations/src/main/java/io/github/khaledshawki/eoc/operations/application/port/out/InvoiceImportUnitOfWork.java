package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import java.util.function.Supplier;

@FunctionalInterface
public interface InvoiceImportUnitOfWork {

  InvoiceImportResult execute(Supplier<InvoiceImportResult> work);
}
