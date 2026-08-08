package io.github.khaledshawki.eoc.operations.application.port.in;

public interface ImportPaymentsUseCase {

  PaymentImportResult importPage(ImportPaymentsCommand command);
}
