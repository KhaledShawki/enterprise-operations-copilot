package io.github.khaledshawki.eoc.operations.application.port.in;

public interface GetPaymentUseCase {

  PaymentResult get(GetPaymentQuery query);
}
