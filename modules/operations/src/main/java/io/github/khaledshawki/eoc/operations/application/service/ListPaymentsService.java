package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentPageResult;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.util.Objects;

public final class ListPaymentsService implements ListPaymentsUseCase {

  private final PaymentQueryRepository paymentQueryRepository;
  private final OperationsAuthorizationPort authorizationPort;

  public ListPaymentsService(
      PaymentQueryRepository paymentQueryRepository,
      OperationsAuthorizationPort authorizationPort) {
    this.paymentQueryRepository =
        Objects.requireNonNull(paymentQueryRepository, "Payment query repository cannot be null");
    this.authorizationPort =
        Objects.requireNonNull(authorizationPort, "Operations authorization port cannot be null");
  }

  @Override
  public PaymentPageResult list(ListPaymentsQuery query) {
    Objects.requireNonNull(query, "Payment list query cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(query.tenantId());
    if (!authorizationPort.hasPermission(
        query.actor(), tenantId, OperationsPermission.READ_PAYMENTS)) {
      throw new OperationsAccessDeniedException(tenantId, OperationsPermission.READ_PAYMENTS);
    }
    return PaymentPageResult.from(paymentQueryRepository.findPage(query.criteria()));
  }
}
