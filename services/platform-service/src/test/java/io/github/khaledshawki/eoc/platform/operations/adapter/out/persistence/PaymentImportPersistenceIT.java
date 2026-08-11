package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.ConcurrentPaymentImportException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  PaymentImportPersistenceIT.MutableClockConfiguration.class
})
class PaymentImportPersistenceIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID OTHER_SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID IMPORT_BATCH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID PAGE_ACCEPTANCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant FIRST_IMPORT_TIME = Instant.parse("2026-08-08T08:00:00Z");
  private static final Instant SECOND_IMPORT_TIME = Instant.parse("2026-08-08T09:00:00Z");
  private static final Instant THIRD_IMPORT_TIME = Instant.parse("2026-08-08T10:00:00Z");
  private static final Instant FIRST_SOURCE_TIME = Instant.parse("2026-08-07T08:00:00Z");
  private static final Instant SECOND_SOURCE_TIME = Instant.parse("2026-08-07T09:00:00Z");
  private static final Instant THIRD_SOURCE_TIME = Instant.parse("2026-08-07T10:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");

  @Autowired private ImportBusinessPartnersUseCase importBusinessPartnersUseCase;
  @Autowired private ImportPaymentsUseCase importPaymentsUseCase;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private PaymentSourceMappingRepository sourceMappingRepository;
  @Autowired private PaymentImportReceiptRepository receiptRepository;
  @Autowired private PaymentImportUnitOfWork unitOfWork;
  @Autowired private SpringDataPaymentRepository springDataPaymentRepository;
  @Autowired private SpringDataPaymentSourceMappingRepository springDataSourceMappingRepository;
  @Autowired private SpringDataPaymentImportReceiptRepository springDataReceiptRepository;
  @Autowired private SpringDataBusinessPartnerRepository springDataBusinessPartnerRepository;

  @Autowired
  private SpringDataBusinessPartnerSourceMappingRepository
      springDataBusinessPartnerMappingRepository;

  @Autowired
  private SpringDataBusinessPartnerImportReceiptRepository
      springDataBusinessPartnerReceiptRepository;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
    springDataReceiptRepository.deleteAllInBatch();
    springDataSourceMappingRepository.deleteAllInBatch();
    springDataPaymentRepository.deleteAllInBatch();
    springDataBusinessPartnerReceiptRepository.deleteAllInBatch();
    springDataBusinessPartnerMappingRepository.deleteAllInBatch();
    springDataBusinessPartnerRepository.deleteAllInBatch();
    clock.setInstant(FIRST_IMPORT_TIME);
  }

  @Test
  void shouldPersistPaymentSourceMappingAndReceiptWithTenantIsolation() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");

    PaymentImportResult result =
        importPaymentsUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record(
                        "payment-1",
                        "v1",
                        FIRST_SOURCE_TIME,
                        "customer-1",
                        "120.00",
                        LocalDate.parse("2026-08-07"),
                        false))));

    assertEquals(1, result.createdCount());
    assertEquals(FIRST_IMPORT_TIME, result.acceptedAt());
    assertEquals(1, springDataPaymentRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
    assertEquals(1L, eventCount("operations.payment.synchronized.v1"));

    PaymentSourceMapping mapping = mapping(TENANT_ID, SOURCE_SYSTEM_ID, "payment-1");
    Payment payment =
        paymentRepository
            .findById(OperationsTenantId.of(TENANT_ID), mapping.paymentId())
            .orElseThrow();
    assertEquals(Money.of("120.00", EUR), payment.amount());
    assertEquals(LocalDate.parse("2026-08-07"), payment.paymentDate());
    assertEquals(PaymentStatus.RECORDED, payment.status());
    assertEquals(Money.of("120.00", EUR), payment.effectiveAmount());
    assertEquals(64, mapping.payloadFingerprint().value().length());
    assertTrue(
        paymentRepository.findById(OperationsTenantId.of(OTHER_TENANT_ID), payment.id()).isEmpty());
  }

  @Test
  void shouldReplayOriginalReceiptWithoutMutatingPersistedState() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    ImportPaymentsCommand original =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "120.00",
                    LocalDate.parse("2026-08-07"),
                    false)));
    PaymentImportResult first = importPaymentsUseCase.importPage(original);
    PaymentSourceMappingJpaEntity mappingBefore = mappingEntity("payment-1");
    PaymentJpaEntity paymentBefore =
        springDataPaymentRepository.findById(mappingBefore.getPaymentId()).orElseThrow();
    clock.setInstant(SECOND_IMPORT_TIME);

    PaymentImportResult replay = importPaymentsUseCase.importPage(original);

    assertEquals(first, replay);
    PaymentSourceMappingJpaEntity mappingAfter = mappingEntity("payment-1");
    PaymentJpaEntity paymentAfter =
        springDataPaymentRepository.findById(mappingAfter.getPaymentId()).orElseThrow();
    assertEquals(mappingBefore.getVersion(), mappingAfter.getVersion());
    assertEquals(paymentBefore.getVersion(), paymentAfter.getVersion());
    assertEquals(FIRST_IMPORT_TIME, mappingAfter.getUpdatedAt());
    assertEquals(FIRST_IMPORT_TIME, paymentAfter.getUpdatedAt());
    assertEquals(1L, eventCount("operations.payment.synchronized.v1"));
  }

  @Test
  void shouldPersistReversalAndReopeningOnlyForNewerEvidence() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-2");
    importPaymentsUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "100.00",
                    LocalDate.parse("2026-08-07"),
                    false))));

    clock.setInstant(SECOND_IMPORT_TIME);
    PaymentImportResult reversed =
        importPaymentsUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "payment-1",
                        "v2",
                        SECOND_SOURCE_TIME,
                        "customer-2",
                        "120.00",
                        USD,
                        LocalDate.parse("2026-08-08"),
                        true))));
    Payment reversedPayment = payment(TENANT_ID, SOURCE_SYSTEM_ID, "payment-1");
    assertTrue(reversedPayment.reversed());
    assertEquals(PaymentStatus.REVERSED, reversedPayment.status());
    assertEquals(Money.of("120.00", USD), reversedPayment.amount());
    assertEquals(Money.zero(USD), reversedPayment.effectiveAmount());

    clock.setInstant(THIRD_IMPORT_TIME);
    PaymentImportResult reopened =
        importPaymentsUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "payment-1",
                        "v3",
                        THIRD_SOURCE_TIME,
                        "customer-2",
                        "130.00",
                        USD,
                        LocalDate.parse("2026-08-09"),
                        false))));
    PaymentImportResult stale =
        importPaymentsUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "payment-1",
                        "v0",
                        FIRST_SOURCE_TIME.minusSeconds(1),
                        "customer-1",
                        "1.00",
                        LocalDate.parse("2026-08-01"),
                        true))));

    assertEquals(1, reversed.updatedCount());
    assertEquals(1, reopened.updatedCount());
    assertEquals(1, stale.staleCount());
    PaymentSourceMappingJpaEntity mapping = mappingEntity("payment-1");
    PaymentJpaEntity entity =
        springDataPaymentRepository.findById(mapping.getPaymentId()).orElseThrow();
    Payment payment =
        paymentRepository
            .findById(OperationsTenantId.of(TENANT_ID), PaymentId.of(entity.getId()))
            .orElseThrow();
    assertEquals("v3", mapping.getSourceVersion());
    assertEquals(Long.valueOf(2L), mapping.getVersion());
    assertEquals(Long.valueOf(2L), entity.getVersion());
    assertEquals(THIRD_IMPORT_TIME, mapping.getUpdatedAt());
    assertEquals(THIRD_IMPORT_TIME, entity.getUpdatedAt());
    assertFalse(payment.reversed());
    assertEquals(Money.of("130.00", USD), payment.amount());
    assertEquals(PaymentStatus.RECORDED, payment.status());
  }

  @Test
  void shouldRejectChangedPageReplayWithoutMutation() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importPaymentsUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "120.00",
                    LocalDate.parse("2026-08-07"),
                    false))));

    ImportPaymentsCommand conflict =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-2",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "10.00",
                    LocalDate.parse("2026-08-07"),
                    false)));

    assertThrows(
        ImportPageAcceptanceConflictException.class,
        () -> importPaymentsUseCase.importPage(conflict));
    assertEquals(1, springDataPaymentRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
  }

  @Test
  void shouldRollBackUnitOfWorkWhenPersistenceFailsAfterPaymentWrite() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    var customerMapping =
        springDataBusinessPartnerMappingRepository.findAll().stream().findFirst().orElseThrow();
    Payment payment =
        Payment.importCustomerPayment(
            OperationsTenantId.of(TENANT_ID),
            io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId.of(
                customerMapping.getBusinessPartnerId()),
            Money.of("10.00", EUR),
            LocalDate.parse("2026-08-08"),
            false);

    assertThrows(
        IllegalStateException.class,
        () ->
            unitOfWork.execute(
                () -> {
                  paymentRepository.save(payment);
                  throw new IllegalStateException("force rollback");
                }));

    assertEquals(0, springDataPaymentRepository.count());
  }

  @Test
  void shouldRejectMissingCustomerAndConflictingReplayWithoutPartialWrites() {
    assertThrows(
        PaymentCustomerSourceMappingNotFoundException.class,
        () ->
            importPaymentsUseCase.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "missing-customer-payment",
                            "v1",
                            FIRST_SOURCE_TIME,
                            "missing-customer",
                            "10.00",
                            LocalDate.parse("2026-08-07"),
                            false)))));
    assertEquals(0, springDataPaymentRepository.count());
    assertEquals(0, springDataReceiptRepository.count());

    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importPaymentsUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            UUID.randomUUID(),
            List.of(
                record(
                    "payment-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "10.00",
                    LocalDate.parse("2026-08-07"),
                    false))));
    long paymentCount = springDataPaymentRepository.count();
    long mappingCount = springDataSourceMappingRepository.count();

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () ->
            importPaymentsUseCase.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    UUID.randomUUID(),
                    List.of(
                        record(
                            "new-payment",
                            "v1",
                            SECOND_SOURCE_TIME,
                            "customer-1",
                            "5.00",
                            LocalDate.parse("2026-08-08"),
                            false),
                        record(
                            "payment-1",
                            "v1",
                            FIRST_SOURCE_TIME,
                            "customer-1",
                            "11.00",
                            LocalDate.parse("2026-08-07"),
                            false)))));

    assertEquals(paymentCount, springDataPaymentRepository.count());
    assertEquals(mappingCount, springDataSourceMappingRepository.count());
    assertTrue(
        sourceMappingRepository
            .findBySourceIdentity(
                OperationsTenantId.of(TENANT_ID),
                SourceSystemId.of(SOURCE_SYSTEM_ID),
                SourceRecordIdentity.sourceRecordId("new-payment"))
            .isEmpty());
  }

  @Test
  void shouldScopeMappingsAndReceiptsByTenantAndSource() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer");
    seedCustomer(OTHER_TENANT_ID, SOURCE_SYSTEM_ID, "customer");
    seedCustomer(TENANT_ID, OTHER_SOURCE_SYSTEM_ID, "customer");

    for (ImportPaymentsCommand command :
        List.of(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record(
                        "shared",
                        "v1",
                        FIRST_SOURCE_TIME,
                        "customer",
                        "10.00",
                        LocalDate.parse("2026-08-07"),
                        false))),
            command(
                OTHER_TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record(
                        "shared",
                        "v1",
                        FIRST_SOURCE_TIME,
                        "customer",
                        "20.00",
                        LocalDate.parse("2026-08-07"),
                        false))),
            command(
                TENANT_ID,
                OTHER_SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record(
                        "shared",
                        "v1",
                        FIRST_SOURCE_TIME,
                        "customer",
                        "30.00",
                        LocalDate.parse("2026-08-07"),
                        false))))) {
      importPaymentsUseCase.importPage(command);
    }

    assertEquals(3, springDataPaymentRepository.count());
    assertEquals(3, springDataSourceMappingRepository.count());
    assertEquals(3, springDataReceiptRepository.count());
    assertTrue(mappingOptional(TENANT_ID, SOURCE_SYSTEM_ID, "shared").isPresent());
    assertTrue(mappingOptional(OTHER_TENANT_ID, SOURCE_SYSTEM_ID, "shared").isPresent());
    assertTrue(mappingOptional(TENANT_ID, OTHER_SOURCE_SYSTEM_ID, "shared").isPresent());
  }

  @Test
  void shouldRejectStaleJpaPaymentVersions() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importPaymentsUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "10.00",
                    LocalDate.parse("2026-08-08"),
                    false))));
    UUID paymentId = mappingEntity("payment-1").getPaymentId();
    PaymentJpaEntity first = springDataPaymentRepository.findById(paymentId).orElseThrow();
    PaymentJpaEntity stale = springDataPaymentRepository.findById(paymentId).orElseThrow();

    first.updateMutableState(
        first.getCustomerId(),
        first.getCurrencyCode(),
        Money.of("11.00", EUR).amount(),
        first.getPaymentDate(),
        false,
        SECOND_IMPORT_TIME);
    springDataPaymentRepository.saveAndFlush(first);

    stale.updateMutableState(
        stale.getCustomerId(),
        stale.getCurrencyCode(),
        Money.of("12.00", EUR).amount(),
        stale.getPaymentDate(),
        false,
        THIRD_IMPORT_TIME);
    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () -> springDataPaymentRepository.saveAndFlush(stale));

    PaymentJpaEntity persisted = springDataPaymentRepository.findById(paymentId).orElseThrow();
    assertEquals(0, persisted.getAmount().compareTo(Money.of("11.00", EUR).amount()));
    assertEquals(Long.valueOf(1L), persisted.getVersion());
  }

  @Test
  void shouldRoundTripCanonicalRecordHashSourceIdentity() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    SourceRecordIdentity canonicalIdentity =
        SourceRecordIdentity.canonicalRecordHash("b".repeat(64));
    PaymentImportRecord record =
        new PaymentImportRecord(
            canonicalIdentity,
            new SourceRecordVersion("v1"),
            Optional.of(FIRST_SOURCE_TIME),
            SourceRecordIdentity.sourceRecordId("customer-1"),
            Money.of("25.00", EUR),
            LocalDate.parse("2026-08-08"),
            false);

    importPaymentsUseCase.importPage(
        command(TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of(record)));

    PaymentSourceMapping persisted =
        sourceMappingRepository
            .findBySourceIdentity(
                OperationsTenantId.of(TENANT_ID),
                SourceSystemId.of(SOURCE_SYSTEM_ID),
                canonicalIdentity)
            .orElseThrow();
    assertEquals(canonicalIdentity, persisted.sourceIdentity());
    assertEquals(1, springDataPaymentRepository.count());
  }

  @Test
  void shouldEnforcePositivePaymentAmountInPostgres() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    UUID customerId = springDataBusinessPartnerRepository.findAll().getFirst().getId();
    PaymentJpaEntity zeroAmount =
        paymentEntity(TENANT_ID, customerId, "EUR", java.math.BigDecimal.ZERO);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> springDataPaymentRepository.saveAndFlush(zeroAmount));
    assertEquals(0, springDataPaymentRepository.count());
  }

  @Test
  void shouldEnforceCanonicalCurrencyCodeInPostgres() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    UUID customerId = springDataBusinessPartnerRepository.findAll().getFirst().getId();
    PaymentJpaEntity invalidCurrency =
        paymentEntity(TENANT_ID, customerId, "eur", java.math.BigDecimal.ONE);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> springDataPaymentRepository.saveAndFlush(invalidCurrency));
    assertEquals(0, springDataPaymentRepository.count());
  }

  @Test
  void shouldEnforceTenantScopedCustomerForeignKeyInPostgres() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    UUID customerId = springDataBusinessPartnerRepository.findAll().getFirst().getId();
    PaymentJpaEntity crossTenantCustomer =
        paymentEntity(OTHER_TENANT_ID, customerId, "EUR", java.math.BigDecimal.ONE);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> springDataPaymentRepository.saveAndFlush(crossTenantCustomer));
    assertEquals(0, springDataPaymentRepository.count());
  }

  @Test
  void shouldRejectOrphanPaymentSourceMappingsInPostgres() {
    PaymentSourceMappingJpaEntity orphanMapping =
        new PaymentSourceMappingJpaEntity(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SourceRecordIdentity.Kind.SOURCE_RECORD_ID,
            "orphan-payment",
            UUID.randomUUID(),
            "v1",
            FIRST_SOURCE_TIME,
            "c".repeat(64),
            FIRST_IMPORT_TIME,
            FIRST_IMPORT_TIME);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> springDataSourceMappingRepository.saveAndFlush(orphanMapping));
    assertEquals(0, springDataSourceMappingRepository.count());
  }

  @Test
  void shouldEnforceImportReceiptCountReconciliationInPostgres() {
    PaymentImportReceiptJpaEntity invalidCounts =
        new PaymentImportReceiptJpaEntity(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            UUID.randomUUID(),
            "d".repeat(64),
            1,
            0,
            0,
            0,
            0,
            FIRST_IMPORT_TIME);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> springDataReceiptRepository.saveAndFlush(invalidCounts));
    assertEquals(0, springDataReceiptRepository.count());
  }

  @Test
  void shouldRejectConcurrentPageReceiptInsertion() {
    PaymentImportReceipt receipt =
        new PaymentImportReceipt(
            "a".repeat(64),
            new PaymentImportResult(PAGE_ACCEPTANCE_ID, 0, 0, 0, 0, 0, FIRST_IMPORT_TIME));

    receiptRepository.save(
        OperationsTenantId.of(TENANT_ID),
        SourceSystemId.of(SOURCE_SYSTEM_ID),
        IMPORT_BATCH_ID,
        receipt);

    assertThrows(
        ConcurrentPaymentImportException.class,
        () ->
            receiptRepository.save(
                OperationsTenantId.of(TENANT_ID),
                SourceSystemId.of(SOURCE_SYSTEM_ID),
                IMPORT_BATCH_ID,
                receipt));
    assertEquals(1, springDataReceiptRepository.count());
  }

  private static PaymentJpaEntity paymentEntity(
      UUID tenantId, UUID customerId, String currencyCode, java.math.BigDecimal amount) {
    return new PaymentJpaEntity(
        UUID.randomUUID(),
        tenantId,
        customerId,
        currencyCode,
        amount,
        LocalDate.parse("2026-08-08"),
        false,
        FIRST_IMPORT_TIME,
        FIRST_IMPORT_TIME);
  }

  private Payment payment(UUID tenantId, UUID sourceSystemId, String sourceIdentity) {
    PaymentSourceMapping sourceMapping = mapping(tenantId, sourceSystemId, sourceIdentity);
    return paymentRepository
        .findById(OperationsTenantId.of(tenantId), sourceMapping.paymentId())
        .orElseThrow();
  }

  private PaymentSourceMapping mapping(UUID tenantId, UUID sourceSystemId, String sourceIdentity) {
    return mappingOptional(tenantId, sourceSystemId, sourceIdentity).orElseThrow();
  }

  private Optional<PaymentSourceMapping> mappingOptional(
      UUID tenantId, UUID sourceSystemId, String sourceIdentity) {
    return sourceMappingRepository.findBySourceIdentity(
        OperationsTenantId.of(tenantId),
        SourceSystemId.of(sourceSystemId),
        SourceRecordIdentity.sourceRecordId(sourceIdentity));
  }

  private PaymentSourceMappingJpaEntity mappingEntity(String sourceIdentity) {
    return springDataSourceMappingRepository
        .findById(
            new PaymentSourceMappingJpaId(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SourceRecordIdentity.Kind.SOURCE_RECORD_ID,
                sourceIdentity))
        .orElseThrow();
  }

  private long eventCount(String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM operations_outbox_events WHERE event_type = ?",
        Long.class,
        eventType);
  }

  private void seedCustomer(UUID tenantId, UUID sourceSystemId, String sourceIdentity) {
    importBusinessPartnersUseCase.importPage(
        new ImportBusinessPartnersCommand(
            tenantId,
            sourceSystemId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(
                new BusinessPartnerImportRecord(
                    SourceRecordIdentity.sourceRecordId(sourceIdentity),
                    new SourceRecordVersion("v1"),
                    Optional.of(FIRST_SOURCE_TIME.minusSeconds(60)),
                    new BusinessPartnerProfile(
                        "BP-" + sourceIdentity, "Customer " + sourceIdentity, Optional.empty())))));
  }

  private static ImportPaymentsCommand command(
      UUID tenantId,
      UUID sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId,
      List<PaymentImportRecord> records) {
    return new ImportPaymentsCommand(
        tenantId, sourceSystemId, importBatchId, pageAcceptanceId, records);
  }

  private static PaymentImportRecord record(
      String sourceIdentity,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerSourceIdentity,
      String amount,
      LocalDate paymentDate,
      boolean reversed) {
    return record(
        sourceIdentity,
        sourceVersion,
        sourceModifiedAt,
        customerSourceIdentity,
        amount,
        EUR,
        paymentDate,
        reversed);
  }

  private static PaymentImportRecord record(
      String sourceIdentity,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerSourceIdentity,
      String amount,
      CurrencyCode currency,
      LocalDate paymentDate,
      boolean reversed) {
    return new PaymentImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(sourceVersion),
        Optional.ofNullable(sourceModifiedAt),
        SourceRecordIdentity.sourceRecordId(customerSourceIdentity),
        Money.of(amount, currency),
        paymentDate,
        reversed);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(FIRST_IMPORT_TIME);
    }
  }

  static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Clock instant cannot be null");
    }

    void setInstant(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Clock instant cannot be null");
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      Objects.requireNonNull(zone, "Clock zone cannot be null");
      return zone.equals(ZoneOffset.UTC) ? this : Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
