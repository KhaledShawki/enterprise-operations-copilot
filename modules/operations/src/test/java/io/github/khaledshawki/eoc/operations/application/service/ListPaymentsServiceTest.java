package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentPageResult;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ListPaymentsServiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");

  @Test
  void shouldPassExactCriteriaReturnCanonicalResultsAndRequestPermission() {
    AtomicReference<PaymentQueryCriteria> captured = new AtomicReference<>();
    AtomicReference<OperationsPermission> permission = new AtomicReference<>();
    PaymentQueryRepository repository =
        criteria -> {
          captured.set(criteria);
          return new PaymentQueryPage(List.of(payment()), 0, 50, 1);
        };
    ListPaymentsService service =
        new ListPaymentsService(
            repository,
            (actor, tenantId, requestedPermission) -> {
              permission.set(requestedPermission);
              return true;
            });
    ListPaymentsQuery query = query();

    PaymentPageResult result = service.list(query);

    assertEquals(query.criteria(), captured.get());
    assertEquals(1, result.totalElements());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), result.payments().getFirst().amount());
    assertEquals(OperationsPermission.READ_PAYMENTS, permission.get());
  }

  @Test
  void shouldFailClosedBeforeQueryRepositoryAccessWhenAuthorizationIsDenied() {
    ListPaymentsService service =
        new ListPaymentsService(
            criteria -> {
              throw new AssertionError("Repository must not be called");
            },
            (actor, tenantId, permission) -> false);

    assertThrows(OperationsAccessDeniedException.class, () -> service.list(query()));
  }

  private static ListPaymentsQuery query() {
    return new ListPaymentsQuery(
        ACTOR,
        TENANT_ID.value(),
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        Optional.empty(),
        0,
        50,
        PaymentSortField.PAYMENT_DATE,
        SortDirection.DESC);
  }

  private static Payment payment() {
    return Payment.reconstitute(
        PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
        TENANT_ID,
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        Money.of("100.00", CurrencyCode.of("EUR")),
        LocalDate.of(2026, 8, 6),
        false);
  }
}
