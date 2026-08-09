package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableSettlementResult;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocation;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GetReceivableSettlementServiceTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final PaymentId PAYMENT_ID =
      PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  private static final ReceivableSettlementId SETTLEMENT_ID =
      ReceivableSettlementId.of(UUID.fromString("00000000-0000-0000-0000-000000000004"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000005"));
  private static final OperationsActor ACTOR = new OperationsActor("issuer", "subject");

  @Test
  void shouldReturnFullyUnappliedStateWhenPaymentHasNoSettlement() {
    AtomicReference<OperationsPermission> permission = new AtomicReference<>();
    GetReceivableSettlementService service =
        service(
            payment(false),
            Optional.empty(),
            (actor, tenantId, requestedPermission) -> {
              permission.set(requestedPermission);
              return true;
            });

    ReceivableSettlementResult result = service.get(query());

    assertEquals(Optional.empty(), result.settlementId());
    assertEquals(Money.zero(EUR), result.allocatedAmount());
    assertEquals(Money.of("100.00", EUR), result.unappliedAmount());
    assertTrue(result.allocations().isEmpty());
    assertEquals(OperationsPermission.READ_RECEIVABLE_SETTLEMENTS, permission.get());
  }

  @Test
  void shouldReturnActiveAndReversedHistoryWithCanonicalSummary() {
    ReceivableAllocation active =
        ReceivableAllocation.active(
            ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000006")),
            INVOICE_ID,
            Money.of("60.00", EUR));
    ReceivableAllocation reversed =
        ReceivableAllocation.active(
                ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000007")),
                INVOICE_ID,
                Money.of("10.00", EUR))
            .reverse();
    ReceivableSettlement settlement = settlement(CUSTOMER_ID, EUR, List.of(active, reversed));

    ReceivableSettlementResult result =
        service(payment(false), Optional.of(settlement), allow()).get(query());

    assertEquals(Optional.of(SETTLEMENT_ID), result.settlementId());
    assertEquals(Money.of("60.00", EUR), result.allocatedAmount());
    assertEquals(Money.of("40.00", EUR), result.unappliedAmount());
    assertEquals(2, result.allocations().size());
    assertEquals(ReceivableAllocationState.ACTIVE, result.allocations().getFirst().state());
    assertEquals(ReceivableAllocationState.REVERSED, result.allocations().get(1).state());
  }

  @Test
  void shouldReturnZeroUnappliedWhenReversedPaymentHasOnlyReversedHistory() {
    ReceivableAllocation reversed =
        ReceivableAllocation.active(
                ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000006")),
                INVOICE_ID,
                Money.of("60.00", EUR))
            .reverse();

    ReceivableSettlementResult result =
        service(
                payment(true),
                Optional.of(settlement(CUSTOMER_ID, EUR, List.of(reversed))),
                allow())
            .get(query());

    assertEquals(Money.zero(EUR), result.allocatedAmount());
    assertEquals(Money.zero(EUR), result.unappliedAmount());
  }

  @Test
  void shouldFailClosedBeforeRepositoryAccessWhenAuthorizationIsDenied() {
    PaymentRepository paymentRepository =
        new PaymentRepository() {
          @Override
          public Payment save(Payment payment) {
            throw new AssertionError("Payment repository must not be called");
          }

          @Override
          public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
            throw new AssertionError("Payment repository must not be called");
          }
        };
    ReceivableSettlementRepository settlementRepository = repositoryThrowingIfAccessed();

    GetReceivableSettlementService service =
        new GetReceivableSettlementService(
            paymentRepository, settlementRepository, (actor, tenantId, permission) -> false);

    assertThrows(OperationsAccessDeniedException.class, () -> service.get(query()));
  }

  @Test
  void shouldNotRevealAnotherTenantPaymentAsPresent() {
    PaymentRepository missingPaymentRepository = paymentRepository(Optional.empty());
    GetReceivableSettlementService service =
        new GetReceivableSettlementService(
            missingPaymentRepository, repository(Optional.empty()), allow());

    assertThrows(PaymentNotFoundException.class, () -> service.get(query()));
  }

  @Test
  void shouldRejectNullPaymentLookupResultAsRepositoryContractCorruption() {
    PaymentRepository invalidPaymentRepository =
        new PaymentRepository() {
          @Override
          public Payment save(Payment payment) {
            return payment;
          }

          @Override
          public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
            return null;
          }
        };
    GetReceivableSettlementService service =
        new GetReceivableSettlementService(
            invalidPaymentRepository, repositoryThrowingIfAccessed(), allow());

    assertThrows(ReceivableSettlementStateCorruptedException.class, () -> service.get(query()));
  }

  @Test
  void shouldRejectSettlementReturnedForAnotherTenant() {
    OperationsTenantId otherTenant =
        OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000099"));
    ReceivableSettlement invalid =
        ReceivableSettlement.reconstitute(
            SETTLEMENT_ID, otherTenant, CUSTOMER_ID, PAYMENT_ID, EUR, List.of());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> service(payment(false), Optional.of(invalid), allow()).get(query()));
  }

  @Test
  void shouldRejectSettlementReturnedForAnotherPayment() {
    ReceivableSettlement invalid =
        ReceivableSettlement.reconstitute(
            SETTLEMENT_ID,
            TENANT_ID,
            CUSTOMER_ID,
            PaymentId.of(UUID.fromString("00000000-0000-0000-0000-000000000099")),
            EUR,
            List.of());

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () -> service(payment(false), Optional.of(invalid), allow()).get(query()));
  }

  @Test
  void shouldRejectSettlementWhoseCustomerNoLongerMatchesPayment() {
    BusinessPartnerId otherCustomer =
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000099"));

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            service(payment(false), Optional.of(settlement(otherCustomer, EUR, List.of())), allow())
                .get(query()));
  }

  @Test
  void shouldRejectSettlementWhoseCurrencyNoLongerMatchesPayment() {
    CurrencyCode usd = CurrencyCode.of("USD");

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            service(payment(false), Optional.of(settlement(CUSTOMER_ID, usd, List.of())), allow())
                .get(query()));
  }

  @Test
  void shouldFailClosedWhenCurrentPaymentEffectiveAmountNoLongerSupportsActiveHistory() {
    ReceivableAllocation active =
        ReceivableAllocation.active(
            ReceivableAllocationId.of(UUID.fromString("00000000-0000-0000-0000-000000000006")),
            INVOICE_ID,
            Money.of("60.00", EUR));

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            service(
                    payment(true),
                    Optional.of(settlement(CUSTOMER_ID, EUR, List.of(active))),
                    allow())
                .get(query()));
  }

  @Test
  void shouldRejectNullSettlementLookupResultAsRepositoryContractCorruption() {
    ReceivableSettlementRepository settlementRepository =
        new ReceivableSettlementRepository() {
          @Override
          public ReceivableSettlement save(ReceivableSettlement settlement) {
            return settlement;
          }

          @Override
          public Optional<ReceivableSettlement> findByPaymentId(
              OperationsTenantId tenantId, PaymentId paymentId) {
            return null;
          }

          @Override
          public Optional<ReceivableSettlement> findByAllocationId(
              OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
            return Optional.empty();
          }

          @Override
          public Money activeAllocatedAmountForInvoice(
              OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency) {
            return Money.zero(currency);
          }
        };

    GetReceivableSettlementService service =
        new GetReceivableSettlementService(
            paymentRepository(Optional.of(payment(false))), settlementRepository, allow());

    assertThrows(ReceivableSettlementStateCorruptedException.class, () -> service.get(query()));
  }

  private static GetReceivableSettlementService service(
      Payment payment,
      Optional<ReceivableSettlement> settlement,
      OperationsAuthorizationPort authorizationPort) {
    return new GetReceivableSettlementService(
        paymentRepository(Optional.of(payment)), repository(settlement), authorizationPort);
  }

  private static OperationsAuthorizationPort allow() {
    return (actor, tenantId, permission) -> true;
  }

  private static GetReceivableSettlementQuery query() {
    return new GetReceivableSettlementQuery(ACTOR, TENANT_ID.value(), PAYMENT_ID.value());
  }

  private static Payment payment(boolean reversed) {
    return Payment.reconstitute(
        PAYMENT_ID,
        TENANT_ID,
        CUSTOMER_ID,
        Money.of("100.00", EUR),
        LocalDate.of(2026, 8, 9),
        reversed);
  }

  private static ReceivableSettlement settlement(
      BusinessPartnerId customerId, CurrencyCode currency, List<ReceivableAllocation> allocations) {
    return ReceivableSettlement.reconstitute(
        SETTLEMENT_ID, TENANT_ID, customerId, PAYMENT_ID, currency, allocations);
  }

  private static PaymentRepository paymentRepository(Optional<Payment> result) {
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

  private static ReceivableSettlementRepository repository(Optional<ReceivableSettlement> result) {
    return new ReceivableSettlementRepository() {
      @Override
      public ReceivableSettlement save(ReceivableSettlement settlement) {
        return settlement;
      }

      @Override
      public Optional<ReceivableSettlement> findByPaymentId(
          OperationsTenantId tenantId, PaymentId paymentId) {
        assertEquals(TENANT_ID, tenantId);
        assertEquals(PAYMENT_ID, paymentId);
        return result;
      }

      @Override
      public Optional<ReceivableSettlement> findByAllocationId(
          OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
        throw new AssertionError("Allocation lookup must not be used by the settlement read");
      }

      @Override
      public Money activeAllocatedAmountForInvoice(
          OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency) {
        throw new AssertionError(
            "Invoice allocation total must not be used by the settlement read");
      }
    };
  }

  private static ReceivableSettlementRepository repositoryThrowingIfAccessed() {
    return new ReceivableSettlementRepository() {
      @Override
      public ReceivableSettlement save(ReceivableSettlement settlement) {
        throw new AssertionError("Settlement repository must not be called");
      }

      @Override
      public Optional<ReceivableSettlement> findByPaymentId(
          OperationsTenantId tenantId, PaymentId paymentId) {
        throw new AssertionError("Settlement repository must not be called");
      }

      @Override
      public Optional<ReceivableSettlement> findByAllocationId(
          OperationsTenantId tenantId, ReceivableAllocationId allocationId) {
        throw new AssertionError("Settlement repository must not be called");
      }

      @Override
      public Money activeAllocatedAmountForInvoice(
          OperationsTenantId tenantId, InvoiceId invoiceId, CurrencyCode currency) {
        throw new AssertionError("Settlement repository must not be called");
      }
    };
  }
}
