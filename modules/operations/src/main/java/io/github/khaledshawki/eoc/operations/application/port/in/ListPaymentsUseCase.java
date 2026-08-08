package io.github.khaledshawki.eoc.operations.application.port.in;

public interface ListPaymentsUseCase {

  PaymentPageResult list(ListPaymentsQuery query);
}
