package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportFingerprint;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportPaymentsServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_BATCH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID PAGE_ACCEPTANCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");

  private InMemoryPaymentRepository paymentRepository;
  private InMemoryPaymentSourceMappingRepository sourceMappingRepository;
  private InMemoryPaymentImportReceiptRepository receiptRepository;
  private InMemoryBusinessPartnerRepository businessPartnerRepository;
  private InMemoryBusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository;
  private DirectPaymentImportUnitOfWork unitOfWork;
  private ImportPaymentsService service;

  @BeforeEach
  void setUp() {
    paymentRepository = new InMemoryPaymentRepository();
    sourceMappingRepository = new InMemoryPaymentSourceMappingRepository();
    receiptRepository = new InMemoryPaymentImportReceiptRepository();
    businessPartnerRepository = new InMemoryBusinessPartnerRepository();
    businessPartnerSourceMappingRepository = new InMemoryBusinessPartnerSourceMappingRepository();
    unitOfWork = new DirectPaymentImportUnitOfWork();
    service =
        new ImportPaymentsService(
            paymentRepository,
            sourceMappingRepository,
            receiptRepository,
            businessPartnerRepository,
            businessPartnerSourceMappingRepository,
            unitOfWork,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void shouldCreateCustomerPaymentsAndPersistOnePageReceipt() {
    BusinessPartner customer = registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord record =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);

    PaymentImportResult result = service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(record)));

    assertEquals(1, result.fetchedCount());
    assertEquals(1, result.createdCount());
    assertEquals(0, result.updatedCount());
    assertEquals(0, result.duplicateCount());
    assertEquals(0, result.staleCount());
    assertEquals(1, result.acceptedCount());
    assertEquals(NOW, result.acceptedAt());
    assertEquals(1, paymentRepository.values.size());
    assertEquals(1, sourceMappingRepository.values.size());
    assertEquals(1, receiptRepository.values.size());
    assertEquals(1, unitOfWork.executionCount);

    Payment payment = onlyPayment();
    PaymentSourceMapping mapping = onlyMapping();
    assertEquals(customer.id(), payment.customerId());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), payment.amount());
    assertEquals(LocalDate.parse("2026-08-01"), payment.paymentDate());
    assertEquals(PaymentStatus.RECORDED, payment.status());
    assertEquals(payment.id(), mapping.paymentId());
    assertEquals(PaymentImportFingerprint.record(record), mapping.payloadFingerprint());
  }

  @Test
  void shouldSynchronizePaymentOnlyForNewerOrderedEvidenceIncludingReversalAndReopening() {
    BusinessPartner firstCustomer = registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    BusinessPartner secondCustomer = registerPartner("customer-2", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false))));
    PaymentSourceMapping originalMapping = onlyMapping();
    Payment originalPayment = onlyPayment();

    PaymentImportRecord reversal =
        new PaymentImportRecord(
            SourceRecordIdentity.sourceRecordId("payment-1"),
            new SourceRecordVersion("v2"),
            Optional.of(SOURCE_TIME.plusSeconds(60)),
            SourceRecordIdentity.sourceRecordId("customer-2"),
            Money.of("120.00", CurrencyCode.of("EUR")),
            LocalDate.parse("2026-08-02"),
            true);
    PaymentImportResult reversalResult =
        service.importPage(command(UUID.randomUUID(), List.of(reversal)));

    assertEquals(1, reversalResult.updatedCount());
    Payment reversedPayment = onlyPayment();
    PaymentSourceMapping reversedMapping = onlyMapping();
    assertEquals(secondCustomer.id(), reversedPayment.customerId());
    assertEquals(Money.of("120.00", CurrencyCode.of("EUR")), reversedPayment.amount());
    assertEquals(LocalDate.parse("2026-08-02"), reversedPayment.paymentDate());
    assertEquals(PaymentStatus.REVERSED, reversedPayment.status());
    assertEquals(Money.zero(CurrencyCode.of("EUR")), reversedPayment.effectiveAmount());
    assertEquals(new SourceRecordVersion("v2"), reversedMapping.sourceVersion());
    assertEquals(PaymentImportFingerprint.record(reversal), reversedMapping.payloadFingerprint());
    assertNotSame(originalMapping, reversedMapping);
    assertEquals(new SourceRecordVersion("v1"), originalMapping.sourceVersion());
    assertEquals(firstCustomer.id(), originalPayment.customerId());
    assertEquals(PaymentStatus.RECORDED, originalPayment.status());

    PaymentImportRecord reopening =
        new PaymentImportRecord(
            reversal.sourceIdentity(),
            new SourceRecordVersion("v3"),
            Optional.of(SOURCE_TIME.plusSeconds(120)),
            reversal.customerSourceIdentity(),
            Money.of("125.00", CurrencyCode.of("EUR")),
            LocalDate.parse("2026-08-03"),
            false);
    PaymentImportResult reopenResult =
        service.importPage(command(UUID.randomUUID(), List.of(reopening)));

    assertEquals(1, reopenResult.updatedCount());
    assertEquals(PaymentStatus.RECORDED, onlyPayment().status());
    assertEquals(Money.of("125.00", CurrencyCode.of("EUR")), onlyPayment().effectiveAmount());
    assertEquals(new SourceRecordVersion("v3"), onlyMapping().sourceVersion());
  }

  @Test
  void shouldClassifyExactAndStaleRecordsWithoutWritingCanonicalState() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord current =
        record("payment-1", "v2", SOURCE_TIME.plusSeconds(60), "customer-1", "100.00", false);
    service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(current)));
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    PaymentImportResult result =
        service.importPage(
            command(
                UUID.randomUUID(),
                List.of(
                    current,
                    record("payment-1", "v1", SOURCE_TIME, "customer-1", "999.00", true))));

    assertEquals(1, result.duplicateCount());
    assertEquals(1, result.staleCount());
    assertEquals(0, result.acceptedCount());
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), onlyPayment().amount());
    assertEquals(PaymentStatus.RECORDED, onlyPayment().status());
  }

  @Test
  void shouldReturnOriginalReceiptForExactPageReplay() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    ImportPaymentsCommand command =
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false)));
    PaymentImportResult first = service.importPage(command);
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    PaymentImportResult replay = service.importPage(command);

    assertSame(first, replay);
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.saveCount);
    assertEquals(2, unitOfWork.executionCount);
  }

  @Test
  void shouldRejectAcceptanceIdReuseWithDifferentPayload() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false))));
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        ImportPageAcceptanceConflictException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record("payment-2", "v1", SOURCE_TIME, "customer-1", "100.00", false)))));
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.saveCount);
  }

  @Test
  void shouldRejectMissingCustomerSourceMappingsBeforeAnyWrite() {
    PaymentImportRecord record =
        record("payment-1", "v1", SOURCE_TIME, "missing-customer", "100.00", false);

    assertThrows(
        PaymentCustomerSourceMappingNotFoundException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(record))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldRejectCustomerMappingsThatReferenceMissingPartners() {
    BusinessPartnerSourceMapping corrupted =
        BusinessPartnerSourceMapping.create(
            tenantId(),
            sourceSystemId(),
            SourceRecordIdentity.sourceRecordId("customer-1"),
            BusinessPartnerId.generate(),
            new SourceRecordVersion("v1"),
            Optional.of(SOURCE_TIME));
    businessPartnerSourceMappingRepository.save(corrupted);

    assertThrows(
        BusinessPartnerSourceMappingCorruptedException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false)))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldRequireResolvedBusinessPartnerToHaveCustomerRole() {
    registerPartner("vendor-1", BusinessPartnerRole.VENDOR);

    assertThrows(
        PaymentCustomerRoleRequiredException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(record("payment-1", "v1", SOURCE_TIME, "vendor-1", "100.00", false)))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldFailWhenAcceptedMappingReferencesMissingPayment() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord existing =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    sourceMappingRepository.save(
        PaymentSourceMapping.create(
            tenantId(),
            sourceSystemId(),
            existing.sourceIdentity(),
            PaymentId.generate(),
            existing.sourceVersion(),
            existing.sourceModifiedAt(),
            PaymentImportFingerprint.record(existing)));
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        PaymentSourceMappingCorruptedException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "payment-1",
                            "v2",
                            SOURCE_TIME.plusSeconds(60),
                            "customer-1",
                            "100.00",
                            false)))));
    assertEquals(0, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertTrue(receiptRepository.values.isEmpty());
  }

  @Test
  void shouldRejectEqualVersionPayloadConflictsWithoutMutatingState() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false))));
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () ->
            service.importPage(
                command(
                    UUID.randomUUID(),
                    List.of(
                        record("payment-1", "v1", SOURCE_TIME, "customer-1", "120.00", false)))));
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.values.size());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), onlyPayment().amount());
  }

  @Test
  void shouldRejectDifferentOpaqueVersionsWithoutComparableTimestamps() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("payment-1", "v1", null, "customer-1", "100.00", false))));
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () ->
            service.importPage(
                command(
                    UUID.randomUUID(),
                    List.of(record("payment-1", "v2", null, "customer-1", "120.00", false)))));
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
  }

  @Test
  void shouldPreflightWholePageBeforePersistingAnything() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord valid =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportRecord invalid =
        record("payment-2", "v1", SOURCE_TIME, "missing-customer", "50.00", false);

    assertThrows(
        PaymentCustomerSourceMappingNotFoundException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(valid, invalid))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldPreflightInvalidPaymentFactsBeforePersistingAnything() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord valid =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportRecord invalid =
        record("payment-2", "v1", SOURCE_TIME, "customer-1", "0.00", false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(valid, invalid))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldStageRepeatedSourceIdentityAndPersistOnlyFinalStateOnce() {
    BusinessPartner firstCustomer = registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    BusinessPartner secondCustomer = registerPartner("customer-2", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord first =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportRecord newer =
        new PaymentImportRecord(
            first.sourceIdentity(),
            new SourceRecordVersion("v2"),
            Optional.of(SOURCE_TIME.plusSeconds(60)),
            SourceRecordIdentity.sourceRecordId("customer-2"),
            Money.of("125.00", CurrencyCode.of("EUR")),
            LocalDate.parse("2026-08-02"),
            true);

    PaymentImportResult result =
        service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(first, newer)));

    assertEquals(2, result.fetchedCount());
    assertEquals(1, result.createdCount());
    assertEquals(1, result.updatedCount());
    assertEquals(1, paymentRepository.saveCount);
    assertEquals(1, sourceMappingRepository.saveCount);
    assertEquals(1, paymentRepository.values.size());
    assertEquals(1, sourceMappingRepository.values.size());
    assertEquals(secondCustomer.id(), onlyPayment().customerId());
    assertEquals(Money.of("125.00", CurrencyCode.of("EUR")), onlyPayment().amount());
    assertEquals(PaymentStatus.REVERSED, onlyPayment().status());
    assertEquals(new SourceRecordVersion("v2"), onlyMapping().sourceVersion());
    assertNotSame(firstCustomer.id(), onlyPayment().customerId());
  }

  @Test
  void shouldRejectConflictingRepeatedSourceIdentityWithoutWritingPartialState() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord first =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportRecord conflict =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "120.00", false);

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(first, conflict))));
    assertNoPaymentWrites();
  }

  @Test
  void shouldAcceptEmptyPagesWithoutCanonicalWrites() {
    PaymentImportResult result = service.importPage(command(PAGE_ACCEPTANCE_ID, List.of()));

    assertEquals(0, result.fetchedCount());
    assertEquals(0, result.acceptedCount());
    assertEquals(0, result.notAppliedCount());
    assertEquals(NOW, result.acceptedAt());
    assertEquals(0, paymentRepository.saveCount);
    assertEquals(0, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.saveCount);
  }

  @Test
  void shouldClassifyStaleRecordsWithoutResolvingTheirCustomer() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "payment-1",
                    "v2",
                    SOURCE_TIME.plusSeconds(60),
                    "customer-1",
                    "100.00",
                    false))));
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    PaymentImportResult result =
        service.importPage(
            command(
                UUID.randomUUID(),
                List.of(
                    record("payment-1", "v1", SOURCE_TIME, "missing-customer", "999.00", true))));

    assertEquals(1, result.staleCount());
    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), onlyPayment().amount());
  }

  @Test
  void shouldLeaveExistingCanonicalStateUntouchedWhenLaterPreflightRecordFails() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord original =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(original)));
    Payment originalPayment = onlyPayment();
    PaymentSourceMapping originalMapping = onlyMapping();
    int paymentSaves = paymentRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    PaymentImportRecord stagedUpdate =
        new PaymentImportRecord(
            original.sourceIdentity(),
            new SourceRecordVersion("v2"),
            Optional.of(SOURCE_TIME.plusSeconds(60)),
            original.customerSourceIdentity(),
            Money.of("125.00", CurrencyCode.of("EUR")),
            LocalDate.parse("2026-08-02"),
            true);
    PaymentImportRecord invalidLaterRecord =
        record("payment-2", "v1", SOURCE_TIME, "missing-customer", "50.00", false);

    assertThrows(
        PaymentCustomerSourceMappingNotFoundException.class,
        () ->
            service.importPage(
                command(UUID.randomUUID(), List.of(stagedUpdate, invalidLaterRecord))));

    assertEquals(paymentSaves, paymentRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertSame(originalPayment, onlyPayment());
    assertSame(originalMapping, onlyMapping());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), onlyPayment().amount());
    assertEquals(PaymentStatus.RECORDED, onlyPayment().status());
    assertEquals(new SourceRecordVersion("v1"), onlyMapping().sourceVersion());
  }

  @Test
  void shouldScopePageReceiptsByImportBatch() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    PaymentImportRecord record =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    PaymentImportResult first =
        service.importPage(command(IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of(record)));
    UUID secondBatchId = UUID.fromString("00000000-0000-0000-0000-000000000021");

    PaymentImportResult second =
        service.importPage(command(secondBatchId, PAGE_ACCEPTANCE_ID, List.of(record)));

    assertEquals(1, first.createdCount());
    assertEquals(1, second.duplicateCount());
    assertEquals(2, receiptRepository.saveCount);
    assertEquals(2, receiptRepository.values.size());
  }

  @Test
  void shouldValidateCommandResultReceiptAndDependencies() {
    PaymentImportRecord record =
        record("payment-1", "v1", SOURCE_TIME, "customer-1", "100.00", false);
    List<PaymentImportRecord> mutable = new java.util.ArrayList<>(List.of(record));
    ImportPaymentsCommand command =
        new ImportPaymentsCommand(
            TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, mutable);
    mutable.clear();

    assertEquals(1, command.records().size());
    assertThrows(UnsupportedOperationException.class, () -> command.records().add(record));
    assertThrows(
        NullPointerException.class,
        () ->
            new ImportPaymentsCommand(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                java.util.Arrays.asList(record, null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImportPaymentsCommand(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                java.util.Collections.nCopies(
                    ImportPaymentsCommand.MAX_RECORDS_PER_PAGE + 1, record)));

    PaymentImportResult result = new PaymentImportResult(PAGE_ACCEPTANCE_ID, 2, 1, 0, 1, 0, NOW);
    assertEquals(1, result.acceptedCount());
    assertEquals(1, result.notAppliedCount());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentImportResult(PAGE_ACCEPTANCE_ID, 1, 1, 1, 0, 0, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentImportResult(PAGE_ACCEPTANCE_ID, -1, 0, 0, 0, 0, NOW));

    PaymentImportReceipt receipt = new PaymentImportReceipt("A".repeat(64), result);
    assertEquals("a".repeat(64), receipt.payloadFingerprint());
    assertThrows(
        IllegalArgumentException.class,
        () -> new PaymentImportReceipt("not-a-fingerprint", result));

    assertThrows(
        NullPointerException.class,
        () ->
            new ImportPaymentsService(
                null,
                sourceMappingRepository,
                receiptRepository,
                businessPartnerRepository,
                businessPartnerSourceMappingRepository,
                unitOfWork,
                Clock.systemUTC()));
    assertThrows(NullPointerException.class, () -> service.importPage(null));
  }

  private ImportPaymentsCommand command(UUID pageAcceptanceId, List<PaymentImportRecord> records) {
    return command(IMPORT_BATCH_ID, pageAcceptanceId, records);
  }

  private ImportPaymentsCommand command(
      UUID importBatchId, UUID pageAcceptanceId, List<PaymentImportRecord> records) {
    return new ImportPaymentsCommand(
        TENANT_ID, SOURCE_SYSTEM_ID, importBatchId, pageAcceptanceId, records);
  }

  private PaymentImportRecord record(
      String sourceIdentity,
      String version,
      Instant modifiedAt,
      String customerIdentity,
      String amount,
      boolean reversed) {
    return new PaymentImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(version),
        Optional.ofNullable(modifiedAt),
        SourceRecordIdentity.sourceRecordId(customerIdentity),
        Money.of(amount, CurrencyCode.of("EUR")),
        LocalDate.parse("2026-08-01"),
        reversed);
  }

  private BusinessPartner registerPartner(String sourceIdentity, BusinessPartnerRole... roles) {
    BusinessPartner partner =
        BusinessPartner.reconstitute(
            BusinessPartnerId.generate(),
            tenantId(),
            new BusinessPartnerProfile(
                "BP-" + sourceIdentity, "Partner " + sourceIdentity, Optional.empty()),
            EnumSet.copyOf(List.of(roles)));
    businessPartnerRepository.save(partner);
    businessPartnerSourceMappingRepository.save(
        BusinessPartnerSourceMapping.create(
            tenantId(),
            sourceSystemId(),
            SourceRecordIdentity.sourceRecordId(sourceIdentity),
            partner.id(),
            new SourceRecordVersion("v1"),
            Optional.of(SOURCE_TIME)));
    return partner;
  }

  private Payment onlyPayment() {
    return paymentRepository.values.values().iterator().next();
  }

  private PaymentSourceMapping onlyMapping() {
    return sourceMappingRepository.values.values().iterator().next();
  }

  private void assertNoPaymentWrites() {
    assertEquals(0, paymentRepository.saveCount);
    assertEquals(0, sourceMappingRepository.saveCount);
    assertTrue(receiptRepository.values.isEmpty());
  }

  private static OperationsTenantId tenantId() {
    return OperationsTenantId.of(TENANT_ID);
  }

  private static SourceSystemId sourceSystemId() {
    return SourceSystemId.of(SOURCE_SYSTEM_ID);
  }

  private static final class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<PaymentId, Payment> values = new HashMap<>();
    private int saveCount;

    @Override
    public Payment save(Payment payment) {
      saveCount++;
      values.put(payment.id(), payment);
      return payment;
    }

    @Override
    public Optional<Payment> findById(OperationsTenantId tenantId, PaymentId paymentId) {
      return Optional.ofNullable(values.get(paymentId))
          .filter(payment -> payment.tenantId().equals(tenantId));
    }
  }

  private static final class InMemoryPaymentSourceMappingRepository
      implements PaymentSourceMappingRepository {

    private final Map<SourceRecordIdentity, PaymentSourceMapping> values = new HashMap<>();
    private int saveCount;

    @Override
    public PaymentSourceMapping save(PaymentSourceMapping sourceMapping) {
      saveCount++;
      values.put(sourceMapping.sourceIdentity(), sourceMapping);
      return sourceMapping;
    }

    @Override
    public Optional<PaymentSourceMapping> findBySourceIdentity(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        SourceRecordIdentity sourceIdentity) {
      return Optional.ofNullable(values.get(sourceIdentity))
          .filter(mapping -> mapping.tenantId().equals(tenantId))
          .filter(mapping -> mapping.sourceSystemId().equals(sourceSystemId));
    }
  }

  private static final class InMemoryPaymentImportReceiptRepository
      implements PaymentImportReceiptRepository {

    private final Map<ReceiptKey, PaymentImportReceipt> values = new HashMap<>();
    private int saveCount;

    @Override
    public Optional<PaymentImportReceipt> find(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        UUID pageAcceptanceId) {
      return Optional.ofNullable(
          values.get(new ReceiptKey(tenantId, sourceSystemId, importBatchId, pageAcceptanceId)));
    }

    @Override
    public PaymentImportReceipt save(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        PaymentImportReceipt receipt) {
      saveCount++;
      values.put(
          new ReceiptKey(
              tenantId, sourceSystemId, importBatchId, receipt.result().pageAcceptanceId()),
          receipt);
      return receipt;
    }

    private record ReceiptKey(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        UUID pageAcceptanceId) {}
  }

  private static final class InMemoryBusinessPartnerRepository
      implements BusinessPartnerRepository {

    private final Map<BusinessPartnerId, BusinessPartner> values = new HashMap<>();

    @Override
    public BusinessPartner save(BusinessPartner businessPartner) {
      values.put(businessPartner.id(), businessPartner);
      return businessPartner;
    }

    @Override
    public Optional<BusinessPartner> findById(
        OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {
      return Optional.ofNullable(values.get(businessPartnerId))
          .filter(partner -> partner.tenantId().equals(tenantId));
    }
  }

  private static final class InMemoryBusinessPartnerSourceMappingRepository
      implements BusinessPartnerSourceMappingRepository {

    private final Map<SourceRecordIdentity, BusinessPartnerSourceMapping> values = new HashMap<>();

    @Override
    public BusinessPartnerSourceMapping save(BusinessPartnerSourceMapping sourceMapping) {
      values.put(sourceMapping.sourceIdentity(), sourceMapping);
      return sourceMapping;
    }

    @Override
    public Optional<BusinessPartnerSourceMapping> findBySourceIdentity(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        SourceRecordIdentity sourceIdentity) {
      return Optional.ofNullable(values.get(sourceIdentity))
          .filter(mapping -> mapping.tenantId().equals(tenantId))
          .filter(mapping -> mapping.sourceSystemId().equals(sourceSystemId));
    }
  }

  private static final class DirectPaymentImportUnitOfWork implements PaymentImportUnitOfWork {

    private int executionCount;

    @Override
    public PaymentImportResult execute(Supplier<PaymentImportResult> work) {
      executionCount++;
      return work.get();
    }
  }
}
