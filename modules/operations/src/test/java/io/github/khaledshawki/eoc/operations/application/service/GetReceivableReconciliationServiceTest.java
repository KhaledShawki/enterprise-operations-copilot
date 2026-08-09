package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableReconciliationStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationEvidence;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableReconciliationResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GetReceivableReconciliationServiceTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000101"));
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000201"));
  private static final BusinessPartnerId CUSTOMER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000301"));
  private static final OperationsActor ACTOR =
      new OperationsActor("issuer", "reconciliation-reader");
  private static final GetReceivableReconciliationQuery QUERY =
      new GetReceivableReconciliationQuery(ACTOR, TENANT_ID.value(), INVOICE_ID.value());

  @Test
  void shouldReportMatchedEvidenceAndRequestDedicatedPermission() {
    AtomicReference<OperationsPermission> permission = new AtomicReference<>();
    GetReceivableReconciliationService service =
        service(
            Optional.of(invoice("100.00", "40.00", false)),
            evidence("40.00", 2, Set.of()),
            (actor, tenant, requestedPermission) -> {
              permission.set(requestedPermission);
              return true;
            });

    ReceivableReconciliationResult result = service.get(QUERY);

    assertEquals(ReceivableReconciliationStatus.MATCHED, result.status());
    assertEquals(Money.of("0.00", EUR), result.difference().orElseThrow());
    assertEquals(OperationsPermission.READ_RECEIVABLE_RECONCILIATIONS, permission.get());
  }

  @Test
  void shouldReportSourceAheadWhenSourcePaidExceedsTraceableLocalAllocations() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("100.00", "80.00", false)),
                evidence("60.00", 2, Set.of()),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.SOURCE_AHEAD, result.status());
    assertEquals(Money.of("20.00", EUR), result.difference().orElseThrow());
  }

  @Test
  void shouldReportLocalAheadWhenLocalAllocationsExceedSourcePaidEvidence() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("100.00", "20.00", false)),
                evidence("45.00", 2, Set.of()),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.LOCAL_AHEAD, result.status());
    assertEquals(Money.of("-25.00", EUR), result.difference().orElseThrow());
  }

  @Test
  void shouldTreatUnallocatedSourcePaidInvoiceAsSourceAhead() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("100.00", "25.00", false)),
                evidence("0.00", 0, Set.of()),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.SOURCE_AHEAD, result.status());
    assertEquals(0, result.activeAllocationCount());
  }

  @Test
  void shouldSurfaceCancelledInvoiceWithActiveAllocationsAsConflict() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("100.00", "40.00", true)),
                evidence("40.00", 1, Set.of()),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.CONFLICT, result.status());
    assertTrue(
        result
            .issues()
            .contains(ReceivableReconciliationIssue.INVOICE_CANCELLED_WITH_ACTIVE_ALLOCATIONS));
    assertTrue(result.difference().isEmpty());
  }

  @Test
  void shouldSurfaceInvoiceAmountShrinkBelowLocalAllocationsAsConflict() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("50.00", "20.00", false)),
                evidence("60.00", 2, Set.of()),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.CONFLICT, result.status());
    assertTrue(
        result
            .issues()
            .contains(ReceivableReconciliationIssue.INVOICE_ALLOCATION_CAPACITY_EXCEEDED));
  }

  @Test
  void shouldPreserveInfrastructureStructuralIssuesAsConflictEvidence() {
    ReceivableReconciliationResult result =
        service(
                Optional.of(invoice("100.00", "40.00", false)),
                evidence(
                    "40.00",
                    1,
                    Set.of(
                        ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS,
                        ReceivableReconciliationIssue.PAYMENT_CUSTOMER_MISMATCH)),
                allow())
            .get(QUERY);

    assertEquals(ReceivableReconciliationStatus.CONFLICT, result.status());
    assertEquals(2, result.issues().size());
    assertTrue(result.difference().isEmpty());
  }

  @Test
  void shouldPreserveUnrepresentableLocalAmountForAllocationCurrencyConflict() {
    ReceivableReconciliationEvidence evidence =
        new ReceivableReconciliationEvidence(
            Optional.empty(),
            1,
            Set.of(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH));

    ReceivableReconciliationResult result =
        service(Optional.of(invoice("100.00", "40.00", false)), evidence, allow()).get(QUERY);

    assertEquals(ReceivableReconciliationStatus.CONFLICT, result.status());
    assertTrue(result.localAllocatedAmount().isEmpty());
    assertTrue(result.difference().isEmpty());
  }

  @Test
  void shouldFailClosedBeforeRepositoryAccessWhenAuthorizationIsDenied() {
    AtomicBoolean repositoryCalled = new AtomicBoolean();
    InvoiceRepository repository =
        new InvoiceRepository() {
          @Override
          public Invoice save(Invoice invoice) {
            return invoice;
          }

          @Override
          public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
            repositoryCalled.set(true);
            return Optional.of(invoice("100.00", "0.00", false));
          }
        };
    GetReceivableReconciliationService service =
        new GetReceivableReconciliationService(
            repository,
            (tenantId, invoiceId, customerId, currency) -> evidence("0.00", 0, Set.of()),
            (actor, tenantId, permission) -> false);

    assertThrows(OperationsAccessDeniedException.class, () -> service.get(QUERY));
    assertFalse(repositoryCalled.get());
  }

  @Test
  void shouldNotRevealAnotherTenantInvoiceAsPresent() {
    GetReceivableReconciliationService service =
        service(Optional.empty(), evidence("0.00", 0, Set.of()), allow());

    assertThrows(InvoiceNotFoundException.class, () -> service.get(QUERY));
  }

  @Test
  void shouldFailClosedWhenInvoiceRepositoryReturnsNullLookup() {
    InvoiceRepository repository =
        new InvoiceRepository() {
          @Override
          public Invoice save(Invoice invoice) {
            return invoice;
          }

          @Override
          public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
            return null;
          }
        };
    GetReceivableReconciliationService service =
        new GetReceivableReconciliationService(
            repository,
            (tenantId, invoiceId, customerId, currency) -> evidence("0.00", 0, Set.of()),
            allow());

    assertThrows(ReceivableReconciliationStateCorruptedException.class, () -> service.get(QUERY));
  }

  @Test
  void shouldFailClosedWhenEvidenceRepositoryReturnsNull() {
    GetReceivableReconciliationService service =
        new GetReceivableReconciliationService(
            invoiceRepository(Optional.of(invoice("100.00", "0.00", false))),
            (tenantId, invoiceId, customerId, currency) -> null,
            allow());

    assertThrows(ReceivableReconciliationStateCorruptedException.class, () -> service.get(QUERY));
  }

  private static GetReceivableReconciliationService service(
      Optional<Invoice> invoice,
      ReceivableReconciliationEvidence evidence,
      io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort
          authorizationPort) {
    return new GetReceivableReconciliationService(
        invoiceRepository(invoice),
        (tenantId, invoiceId, customerId, currency) -> {
          assertEquals(TENANT_ID, tenantId);
          assertEquals(INVOICE_ID, invoiceId);
          assertEquals(CUSTOMER_ID, customerId);
          assertEquals(EUR, currency);
          return evidence;
        },
        authorizationPort);
  }

  private static InvoiceRepository invoiceRepository(Optional<Invoice> invoice) {
    return new InvoiceRepository() {
      @Override
      public Invoice save(Invoice saved) {
        return saved;
      }

      @Override
      public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
        assertEquals(TENANT_ID, tenantId);
        assertEquals(INVOICE_ID, invoiceId);
        return invoice;
      }
    };
  }

  private static ReceivableReconciliationEvidence evidence(
      String amount, long count, Set<ReceivableReconciliationIssue> issues) {
    return new ReceivableReconciliationEvidence(Optional.of(Money.of(amount, EUR)), count, issues);
  }

  private static io.github.khaledshawki.eoc.operations.application.port.out
          .OperationsAuthorizationPort
      allow() {
    return (actor, tenantId, permission) -> true;
  }

  private static Invoice invoice(String originalAmount, String paidAmount, boolean cancelled) {
    return Invoice.reconstitute(
        INVOICE_ID,
        TENANT_ID,
        CUSTOMER_ID,
        new InvoiceNumber("INV-RECON"),
        Money.of(originalAmount, EUR),
        Money.of(paidAmount, EUR),
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        cancelled);
  }
}
