package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import java.util.function.Supplier;

@FunctionalInterface
public interface PaymentImportUnitOfWork {

  PaymentImportResult execute(Supplier<PaymentImportResult> work);
}
