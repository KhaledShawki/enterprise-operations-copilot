package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GetPaymentServiceTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final PaymentId PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");

  @Test
  void shouldReturnTenantScopedPaymentAndRequestReadPaymentPermission() {
    AtomicReference<OperationsPermission> permission = new AtomicReference<>();
    GetPaymentService service =
        new GetPaymentService(
            repository(Optional.of(payment(true))),
            (actor, tenantId, requestedPermission) -> {
              permission.set(requestedPermission);
              return true;
            });

    PaymentResult result =
        service.get(new GetPaymentQuery(ACTOR, TENANT_ID.value(), PAYMENT_ID.value()));

    assertEquals(PAYMENT_ID, result.paymentId());
    assertEquals(PaymentStatus.REVERSED, result.status());
    assertEquals(Money.zero(CurrencyCode.of("EUR")), result.effectiveAmount());
    assertEquals(OperationsPermission.READ_PAYMENTS, permission.get());
  }

  @Test
  void shouldFailClosedBeforeRepositoryAccessWhenAuthorizationIsDenied() {
    PaymentRepository repository =
        new PaymentRepository() {
          @Override
          public Payment save(Payment payment) {
            throw new AssertionError("Repository must not be called");
          }

          @Override
          public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
            throw new AssertionError("Repository must not be called");
          }
        };
    GetPaymentService service =
        new GetPaymentService(repository, (actor, tenantId, permission) -> false);

    assertThrows(
        OperationsAccessDeniedException.class,
        () -> service.get(new GetPaymentQuery(ACTOR, TENANT_ID.value(), PAYMENT_ID.value())));
  }

  @Test
  void shouldNotRevealAnotherTenantPaymentAsPresent() {
    GetPaymentService service =
        new GetPaymentService(repository(Optional.empty()), (actor, tenantId, permission) -> true);

    assertThrows(
        PaymentNotFoundException.class,
        () -> service.get(new GetPaymentQuery(ACTOR, TENANT_ID.value(), PAYMENT_ID.value())));
  }

  private static PaymentRepository repository(Optional<Payment> result) {
    return new PaymentRepository() {
      @Override
      public Payment save(Payment payment) {
        return payment;
      }

      @Override
      public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
        assertEquals(TENANT_ID, tenantId);
        assertEquals(PAYMENT_ID, paymentId);
        return result;
      }
    };
  }

  private static Payment payment(boolean reversed) {
    return Payment.reconstitute(
        PAYMENT_ID,
        TENANT_ID,
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        Money.of("100.00", CurrencyCode.of("EUR")),
        LocalDate.of(2026, 8, 6),
        reversed);
  }
}
