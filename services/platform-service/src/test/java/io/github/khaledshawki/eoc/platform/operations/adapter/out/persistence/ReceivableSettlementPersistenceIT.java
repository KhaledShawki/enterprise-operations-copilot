package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.ReceivableInvoiceAllocationCapacityExceededException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementMutationUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlement;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  ReceivableSettlementPersistenceIT.AuthorizationConfiguration.class
})
class ReceivableSettlementPersistenceIT {

  private static final OperationsActor ACTOR = new OperationsActor("issuer", "settlement-test");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID OTHER_CUSTOMER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID OTHER_PAYMENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID OTHER_INVOICE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000402");

  @Autowired private AllocateReceivablePaymentUseCase allocateUseCase;
  @Autowired private ReverseReceivableAllocationUseCase reverseUseCase;
  @Autowired private ReceivableSettlementRepository settlementRepository;
  @Autowired private ReceivableSettlementMutationUnitOfWork unitOfWork;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
    jdbcTemplate.update("DELETE FROM operations_receivable_allocations");
    jdbcTemplate.update("DELETE FROM operations_receivable_settlements");
    jdbcTemplate.update("DELETE FROM operations_receivable_settlement_locks");
    jdbcTemplate.update("DELETE FROM operations_payment_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_payments");
    jdbcTemplate.update("DELETE FROM operations_invoice_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_invoices");
    jdbcTemplate.update("DELETE FROM operations_business_partner_source_mappings");
    jdbcTemplate.update("DELETE FROM operations_business_partner_roles");
    jdbcTemplate.update("DELETE FROM operations_business_partners");
  }

  @Test
  void shouldPersistAndReconstituteSettlementAndActiveInvoiceTotal() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID allocationId = UUID.randomUUID();

    ReceivableAllocationResult result =
        allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId, "60.00");

    assertEquals(ReceivableAllocationState.ACTIVE, result.state());
    ReceivableSettlement settlement =
        settlementRepository
            .findByPaymentId(OperationsTenantId.of(TENANT_ID), PaymentId.of(PAYMENT_ID))
            .orElseThrow();
    assertEquals(result.settlementId(), settlement.id());
    assertEquals(1, settlement.allocations().size());
    assertEquals(Money.of("60.00", EUR), settlement.allocatedAmount());
    assertEquals(
        Money.of("60.00", EUR),
        settlementRepository.activeAllocatedAmountForInvoice(
            OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID), EUR));
    assertEquals(
        result.settlementId(),
        settlementRepository
            .findByAllocationId(
                OperationsTenantId.of(TENANT_ID), ReceivableAllocationId.of(allocationId))
            .orElseThrow()
            .id());
    assertEquals(1L, eventCount("operations.receivable-allocation.applied.v1"));
    assertEquals(1L, rowCount("operations_event_stream_versions"));
  }

  @Test
  void shouldPersistReversalHistoryAndKeepRepeatedReversalIdempotent() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID allocationId = UUID.randomUUID();
    allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId, "40.00");

    ReceivableAllocationResult first = reverse(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId);
    Long versionAfterFirst =
        jdbcTemplate.queryForObject(
            "SELECT version FROM operations_receivable_allocations WHERE tenant_id = ? AND id = ?",
            Long.class,
            TENANT_ID,
            allocationId);
    ReceivableAllocationResult replay = reverse(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId);
    Long versionAfterReplay =
        jdbcTemplate.queryForObject(
            "SELECT version FROM operations_receivable_allocations WHERE tenant_id = ? AND id = ?",
            Long.class,
            TENANT_ID,
            allocationId);

    assertEquals(ReceivableAllocationState.REVERSED, first.state());
    assertEquals(first, replay);
    assertEquals(versionAfterFirst, versionAfterReplay);
    assertEquals(1L, rowCount("operations_receivable_allocations"));
    assertEquals(1L, eventCount("operations.receivable-allocation.applied.v1"));
    assertEquals(1L, eventCount("operations.receivable-allocation.reversed.v1"));
    assertEquals(
        List.of(1L, 2L),
        jdbcTemplate.queryForList(
            "SELECT aggregate_version FROM operations_outbox_events ORDER BY aggregate_version",
            Long.class));
    assertEquals(
        Money.zero(EUR),
        settlementRepository.activeAllocatedAmountForInvoice(
            OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID), EUR));
  }

  @Test
  void shouldScopeCallerSuppliedAllocationIdentityByTenant() {
    UUID allocationId = UUID.randomUUID();
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    seedCustomer(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, "C-2");
    seedPayment(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, OTHER_PAYMENT_ID, "100.00");
    seedInvoice(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, OTHER_INVOICE_ID, "100.00", "0.00");

    ReceivableAllocationResult first =
        allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId, "10.00");
    ReceivableAllocationResult second =
        allocate(OTHER_TENANT_ID, OTHER_PAYMENT_ID, OTHER_INVOICE_ID, allocationId, "20.00");

    assertEquals(ReceivableAllocationState.ACTIVE, first.state());
    assertEquals(ReceivableAllocationState.ACTIVE, second.state());
    assertEquals(2L, rowCount("operations_receivable_allocations"));
    assertTrue(
        settlementRepository
            .findByAllocationId(
                OperationsTenantId.of(TENANT_ID), ReceivableAllocationId.of(allocationId))
            .isPresent());
    assertTrue(
        settlementRepository
            .findByAllocationId(
                OperationsTenantId.of(OTHER_TENANT_ID), ReceivableAllocationId.of(allocationId))
            .isPresent());
  }

  @Test
  void shouldEnforceOneSettlementPerPaymentAtDatabaseBoundary() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    insertSettlement(UUID.randomUUID(), TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertSettlement(UUID.randomUUID(), TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR"));
  }

  @Test
  void shouldRejectCrossTenantPaymentReferenceAtDatabaseBoundary() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedCustomer(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, "C-2");

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            insertSettlement(
                UUID.randomUUID(), OTHER_TENANT_ID, OTHER_CUSTOMER_ID, PAYMENT_ID, "EUR"));
  }

  @Test
  void shouldRejectCrossTenantInvoiceReferenceAtDatabaseBoundary() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedCustomer(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, "C-2");
    seedInvoice(OTHER_TENANT_ID, OTHER_CUSTOMER_ID, OTHER_INVOICE_ID, "100.00", "0.00");
    UUID settlementId = UUID.randomUUID();
    insertSettlement(settlementId, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            insertAllocation(
                UUID.randomUUID(),
                TENANT_ID,
                settlementId,
                OTHER_INVOICE_ID,
                "EUR",
                "10.00",
                "ACTIVE",
                0));
  }

  @Test
  void shouldRejectDuplicateAllocationPositionAtDatabaseBoundary() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID settlementId = UUID.randomUUID();
    insertSettlement(settlementId, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(
        UUID.randomUUID(), TENANT_ID, settlementId, INVOICE_ID, "EUR", "10.00", "ACTIVE", 0);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            insertAllocation(
                UUID.randomUUID(),
                TENANT_ID,
                settlementId,
                INVOICE_ID,
                "EUR",
                "10.00",
                "ACTIVE",
                0));
  }

  @Test
  void shouldRollBackBusinessWriteAndCoordinationRowsWhenMutationFails() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID allocationId = UUID.randomUUID();

    assertThrows(
        IllegalStateException.class,
        () ->
            unitOfWork.execute(
                OperationsTenantId.of(TENANT_ID),
                PaymentId.of(PAYMENT_ID),
                InvoiceId.of(INVOICE_ID),
                ReceivableAllocationId.of(allocationId),
                () -> {
                  insertSettlement(UUID.randomUUID(), TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
                  throw new IllegalStateException("force rollback");
                }));

    assertEquals(0L, rowCount("operations_receivable_settlements"));
    assertEquals(0L, rowCount("operations_receivable_settlement_locks"));
  }

  @Test
  void shouldSerializeConcurrentPaymentsAndPreventInvoiceOverAllocation() throws Exception {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedPayment(TENANT_ID, CUSTOMER_ID, OTHER_PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");

    ConcurrentOutcome outcome =
        runConcurrently(
            () -> allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, UUID.randomUUID(), "80.00"),
            () -> allocate(TENANT_ID, OTHER_PAYMENT_ID, INVOICE_ID, UUID.randomUUID(), "80.00"));

    assertEquals(1, outcome.results().size());
    assertEquals(1, outcome.failures().size());
    assertInstanceOf(
        ReceivableInvoiceAllocationCapacityExceededException.class, outcome.failures().getFirst());
    assertEquals(
        Money.of("80.00", EUR),
        settlementRepository.activeAllocatedAmountForInvoice(
            OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID), EUR));
    assertEquals(1L, activeAllocationCount());
  }

  @Test
  void shouldSerializeConcurrentExactReplayAsSingleAllocation() throws Exception {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID allocationId = UUID.randomUUID();

    ConcurrentOutcome outcome =
        runConcurrently(
            () -> allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId, "60.00"),
            () -> allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, allocationId, "60.00"));

    assertTrue(outcome.failures().isEmpty());
    assertEquals(2, outcome.results().size());
    assertEquals(outcome.results().get(0).settlementId(), outcome.results().get(1).settlementId());
    assertEquals(1L, rowCount("operations_receivable_settlements"));
    assertEquals(1L, rowCount("operations_receivable_allocations"));
  }

  @Test
  void shouldSerializeSamePaymentMutationsAndReuseOneSettlement() throws Exception {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, OTHER_INVOICE_ID, "100.00", "0.00");

    ConcurrentOutcome outcome =
        runConcurrently(
            () -> allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, UUID.randomUUID(), "40.00"),
            () -> allocate(TENANT_ID, PAYMENT_ID, OTHER_INVOICE_ID, UUID.randomUUID(), "40.00"));

    assertTrue(outcome.failures().isEmpty());
    assertEquals(2, outcome.results().size());
    assertEquals(outcome.results().get(0).settlementId(), outcome.results().get(1).settlementId());
    assertEquals(1L, rowCount("operations_receivable_settlements"));
    assertEquals(2L, activeAllocationCount());
  }

  @Test
  void shouldSerializeSamePaymentAndRejectConcurrentPaymentOverAllocation() throws Exception {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, OTHER_INVOICE_ID, "100.00", "0.00");

    ConcurrentOutcome outcome =
        runConcurrently(
            () -> allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, UUID.randomUUID(), "80.00"),
            () -> allocate(TENANT_ID, PAYMENT_ID, OTHER_INVOICE_ID, UUID.randomUUID(), "80.00"));

    assertEquals(1, outcome.results().size());
    assertEquals(1, outcome.failures().size());
    assertInstanceOf(IllegalArgumentException.class, outcome.failures().getFirst());
    assertEquals(1L, rowCount("operations_receivable_settlements"));
    assertEquals(1L, activeAllocationCount());
  }

  @Test
  void shouldFailClosedWhenPersistedAllocationCurrencyDisagreesWithSettlement() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID settlementId = UUID.randomUUID();
    insertSettlement(settlementId, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(
        UUID.randomUUID(), TENANT_ID, settlementId, INVOICE_ID, "USD", "10.00", "ACTIVE", 0);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            settlementRepository.findByPaymentId(
                OperationsTenantId.of(TENANT_ID), PaymentId.of(PAYMENT_ID)));
    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            settlementRepository.activeAllocatedAmountForInvoice(
                OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID), EUR));
  }

  @Test
  void shouldFailClosedWhenPersistedAllocationPositionsAreNotContiguous() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "0.00");
    UUID settlementId = UUID.randomUUID();
    insertSettlement(settlementId, TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "EUR");
    insertAllocation(
        UUID.randomUUID(), TENANT_ID, settlementId, INVOICE_ID, "EUR", "10.00", "ACTIVE", 1);

    assertThrows(
        ReceivableSettlementStateCorruptedException.class,
        () ->
            settlementRepository.findByPaymentId(
                OperationsTenantId.of(TENANT_ID), PaymentId.of(PAYMENT_ID)));
  }

  @Test
  void shouldUseInvoiceOriginalAmountRatherThanSourcePaidAmountForLocalCapacity() {
    seedCustomer(TENANT_ID, CUSTOMER_ID, "C-1");
    seedPayment(TENANT_ID, CUSTOMER_ID, PAYMENT_ID, "100.00");
    seedInvoice(TENANT_ID, CUSTOMER_ID, INVOICE_ID, "100.00", "90.00");

    ReceivableAllocationResult result =
        allocate(TENANT_ID, PAYMENT_ID, INVOICE_ID, UUID.randomUUID(), "100.00");

    assertEquals(ReceivableAllocationState.ACTIVE, result.state());
    assertEquals(
        Money.of("100.00", EUR),
        settlementRepository.activeAllocatedAmountForInvoice(
            OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID), EUR));
  }

  private ReceivableAllocationResult allocate(
      UUID tenantId, UUID paymentId, UUID invoiceId, UUID allocationId, String amount) {
    return allocateUseCase.allocate(
        new AllocateReceivablePaymentCommand(
            ACTOR, tenantId, paymentId, invoiceId, allocationId, Money.of(amount, EUR)));
  }

  private ReceivableAllocationResult reverse(
      UUID tenantId, UUID paymentId, UUID invoiceId, UUID allocationId) {
    return reverseUseCase.reverse(
        new ReverseReceivableAllocationCommand(
            ACTOR, tenantId, paymentId, invoiceId, allocationId));
  }

  private ConcurrentOutcome runConcurrently(
      Callable<ReceivableAllocationResult> first, Callable<ReceivableAllocationResult> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<ReceivableAllocationResult> firstFuture = executor.submit(gated(first, ready, start));
      Future<ReceivableAllocationResult> secondFuture =
          executor.submit(gated(second, ready, start));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      ArrayList<ReceivableAllocationResult> results = new ArrayList<>();
      ArrayList<Throwable> failures = new ArrayList<>();
      collect(firstFuture, results, failures);
      collect(secondFuture, results, failures);
      return new ConcurrentOutcome(List.copyOf(results), List.copyOf(failures));
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private static Callable<ReceivableAllocationResult> gated(
      Callable<ReceivableAllocationResult> work, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent settlement mutation start timed out");
      }
      return work.call();
    };
  }

  private static void collect(
      Future<ReceivableAllocationResult> future,
      List<ReceivableAllocationResult> results,
      List<Throwable> failures)
      throws Exception {
    try {
      results.add(future.get(20, TimeUnit.SECONDS));
    } catch (ExecutionException exception) {
      failures.add(exception.getCause());
    }
  }

  private void seedCustomer(UUID tenantId, UUID customerId, String number) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_business_partners (
          id, tenant_id, partner_number, display_name, email_address, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        customerId,
        tenantId,
        number,
        "Customer " + number);
  }

  private void seedPayment(UUID tenantId, UUID customerId, UUID paymentId, String amount) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_payments (
          id, tenant_id, customer_id, currency_code, amount, payment_date, reversed,
          version, created_at, updated_at
        ) VALUES (?, ?, ?, 'EUR', ?, DATE '2026-08-08', FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        paymentId,
        tenantId,
        customerId,
        new BigDecimal(amount));
  }

  private void seedInvoice(
      UUID tenantId, UUID customerId, UUID invoiceId, String originalAmount, String paidAmount) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_invoices (
          id, tenant_id, customer_id, invoice_number, currency_code, original_amount, paid_amount,
          issue_date, due_date, cancelled, version, created_at, updated_at
        ) VALUES (
          ?, ?, ?, ?, 'EUR', ?, ?, DATE '2026-08-01', DATE '2026-08-31', FALSE,
          0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        )
        """,
        invoiceId,
        tenantId,
        customerId,
        "INV-" + invoiceId.toString().substring(30),
        new BigDecimal(originalAmount),
        new BigDecimal(paidAmount));
  }

  private void insertSettlement(
      UUID settlementId, UUID tenantId, UUID customerId, UUID paymentId, String currency) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_receivable_settlements (
          id, tenant_id, customer_id, payment_id, currency_code, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        settlementId,
        tenantId,
        customerId,
        paymentId,
        currency);
  }

  private void insertAllocation(
      UUID allocationId,
      UUID tenantId,
      UUID settlementId,
      UUID invoiceId,
      String currency,
      String amount,
      String state,
      int position) {
    jdbcTemplate.update(
        """
        INSERT INTO operations_receivable_allocations (
          id, tenant_id, settlement_id, invoice_id, currency_code, amount, state,
          allocation_position, version, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        allocationId,
        tenantId,
        settlementId,
        invoiceId,
        currency,
        new BigDecimal(amount),
        state,
        position);
  }

  private long rowCount(String tableName) {
    Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    return count == null ? 0L : count;
  }

  private long eventCount(String eventType) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operations_outbox_events WHERE event_type = ?",
            Long.class,
            eventType);
    return count == null ? 0L : count;
  }

  private long activeAllocationCount() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operations_receivable_allocations WHERE state = 'ACTIVE'",
            Long.class);
    return count == null ? 0L : count;
  }

  private record ConcurrentOutcome(
      List<ReceivableAllocationResult> results, List<Throwable> failures) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class AuthorizationConfiguration {

    @Bean
    @Primary
    OperationsAuthorizationPort settlementTestAuthorizationPort() {
      return (actor, tenantId, permission) -> true;
    }
  }
}
