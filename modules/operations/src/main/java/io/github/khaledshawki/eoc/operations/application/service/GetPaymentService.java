package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import java.util.Objects;

public final class GetPaymentService implements GetPaymentUseCase {

  private final PaymentRepository paymentRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public GetPaymentService(
      PaymentRepository paymentRepository, OperationsAuthorizationPort authorizationPort) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public PaymentResult get(GetPaymentQuery query) {
    Objects.requireNonNull(query, "Payment query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    authorize(query, tenantId);
    PaymentId paymentId = PaymentId.of(query.paymentId());
    return paymentRepository
        .findById(tenantId, paymentId)
        .map(PaymentResult::from)
        .orElseThrow(() -> new PaymentNotFoundException(tenantId, paymentId));
  }

  private void authorize(GetPaymentQuery query, OperationsTenantId tenantId) {
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_PAYMENTS)) {
      throw new OperationsAccessDeniedException(tenantId, OperationsPermission.READ_PAYMENTS);
    }
  }
}
