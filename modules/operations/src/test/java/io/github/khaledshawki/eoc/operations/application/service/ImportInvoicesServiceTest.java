package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportFingerprint;
import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportInvoicesServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID IMPORT_BATCH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID PAGE_ACCEPTANCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant NOW = Instant.parse("2026-08-04T20:00:00Z");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");

  private InMemoryInvoiceRepository invoiceRepository;
  private InMemoryInvoiceSourceMappingRepository sourceMappingRepository;
  private InMemoryInvoiceImportReceiptRepository receiptRepository;
  private InMemoryBusinessPartnerRepository businessPartnerRepository;
  private InMemoryBusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository;
  private DirectInvoiceImportUnitOfWork unitOfWork;
  private ImportInvoicesService service;

  @BeforeEach
  void setUp() {
    invoiceRepository = new InMemoryInvoiceRepository();
    sourceMappingRepository = new InMemoryInvoiceSourceMappingRepository();
    receiptRepository = new InMemoryInvoiceImportReceiptRepository();
    businessPartnerRepository = new InMemoryBusinessPartnerRepository();
    businessPartnerSourceMappingRepository = new InMemoryBusinessPartnerSourceMappingRepository();
    unitOfWork = new DirectInvoiceImportUnitOfWork();
    service =
        new ImportInvoicesService(
            invoiceRepository,
            sourceMappingRepository,
            receiptRepository,
            businessPartnerRepository,
            businessPartnerSourceMappingRepository,
            unitOfWork,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void shouldCreateCustomerInvoicesAndPersistOnePageReceipt() {
    BusinessPartner customer = registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    InvoiceImportRecord record =
        record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "10.00", false);

    InvoiceImportResult result = service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(record)));

    assertEquals(1, result.fetchedCount());
    assertEquals(1, result.createdCount());
    assertEquals(0, result.updatedCount());
    assertEquals(0, result.duplicateCount());
    assertEquals(0, result.staleCount());
    assertEquals(1, result.acceptedCount());
    assertEquals(NOW, result.acceptedAt());
    assertEquals(1, invoiceRepository.values.size());
    assertEquals(1, sourceMappingRepository.values.size());
    assertEquals(1, receiptRepository.values.size());
    assertEquals(1, unitOfWork.executionCount);

    Invoice invoice = invoiceRepository.values.values().iterator().next();
    InvoiceSourceMapping mapping = sourceMappingRepository.values.values().iterator().next();
    assertEquals(customer.id(), invoice.customerId());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), invoice.originalAmount());
    assertEquals(Money.of("10.00", CurrencyCode.of("EUR")), invoice.paidAmount());
    assertEquals(invoice.id(), mapping.invoiceId());
    assertEquals(InvoiceImportFingerprint.record(record), mapping.payloadFingerprint());
  }

  @Test
  void shouldSynchronizeAnInvoiceOnlyForNewerOrderedEvidence() {
    BusinessPartner firstCustomer = registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    BusinessPartner secondCustomer = registerPartner("customer-2", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(
                record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "10.00", false))));
    InvoiceSourceMapping originalMapping =
        sourceMappingRepository.values.values().iterator().next();
    Invoice originalInvoice = originalInvoice();

    InvoiceImportRecord update =
        new InvoiceImportRecord(
            SourceRecordIdentity.sourceRecordId("invoice-1"),
            new SourceRecordVersion("v2"),
            Optional.of(SOURCE_TIME.plusSeconds(60)),
            SourceRecordIdentity.sourceRecordId("customer-2"),
            new InvoiceNumber("INV-UPDATED"),
            Money.of("120.00", CurrencyCode.of("EUR")),
            Money.of("120.00", CurrencyCode.of("EUR")),
            LocalDate.parse("2026-08-02"),
            LocalDate.parse("2026-09-02"),
            true);
    InvoiceImportResult result = service.importPage(command(UUID.randomUUID(), List.of(update)));

    assertEquals(0, result.createdCount());
    assertEquals(1, result.updatedCount());
    Invoice invoice = invoiceRepository.values.values().iterator().next();
    InvoiceSourceMapping updatedMapping = sourceMappingRepository.values.values().iterator().next();
    assertEquals(secondCustomer.id(), invoice.customerId());
    assertEquals(new InvoiceNumber("INV-UPDATED"), invoice.invoiceNumber());
    assertEquals(Money.of("120.00", CurrencyCode.of("EUR")), invoice.originalAmount());
    assertEquals(Money.of("120.00", CurrencyCode.of("EUR")), invoice.paidAmount());
    assertTrue(invoice.cancelled());
    assertEquals(new SourceRecordVersion("v2"), updatedMapping.sourceVersion());
    assertEquals(InvoiceImportFingerprint.record(update), updatedMapping.payloadFingerprint());
    assertNotSame(originalMapping, updatedMapping);
    assertEquals(new SourceRecordVersion("v1"), originalMapping.sourceVersion());
    assertEquals(firstCustomer.id(), originalInvoice.customerId());
  }

  @Test
  void shouldClassifyExactAndStaleRecordsWithoutWritingCanonicalState() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    InvoiceImportRecord current =
        record(
            "invoice-1", "v2", SOURCE_TIME.plusSeconds(60), "customer-1", "100.00", "10.00", false);
    service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(current)));
    int invoiceSaves = invoiceRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    InvoiceImportResult result =
        service.importPage(
            command(
                UUID.randomUUID(),
                List.of(
                    current,
                    record(
                        "invoice-1", "v1", SOURCE_TIME, "customer-1", "999.00", "0.00", false))));

    assertEquals(1, result.duplicateCount());
    assertEquals(1, result.staleCount());
    assertEquals(0, result.acceptedCount());
    assertEquals(invoiceSaves, invoiceRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), originalInvoice().originalAmount());
  }

  @Test
  void shouldReturnTheOriginalReceiptForAnExactPageReplay() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    ImportInvoicesCommand command =
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false)));
    InvoiceImportResult first = service.importPage(command);
    int invoiceSaves = invoiceRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    InvoiceImportResult replay = service.importPage(command);

    assertSame(first, replay);
    assertEquals(invoiceSaves, invoiceRepository.saveCount);
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
            List.of(
                record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false))));
    int invoiceSaves = invoiceRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        ImportPageAcceptanceConflictException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "invoice-2",
                            "v1",
                            SOURCE_TIME,
                            "customer-1",
                            "100.00",
                            "0.00",
                            false)))));
    assertEquals(invoiceSaves, invoiceRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.saveCount);
  }

  @Test
  void shouldRejectMissingCustomerSourceMappingsBeforeAnyWrite() {
    InvoiceImportRecord record =
        record("invoice-1", "v1", SOURCE_TIME, "missing-customer", "100.00", "0.00", false);

    assertThrows(
        InvoiceCustomerSourceMappingNotFoundException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(record))));
    assertNoInvoiceWrites();
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
                        record(
                            "invoice-1",
                            "v1",
                            SOURCE_TIME,
                            "customer-1",
                            "100.00",
                            "0.00",
                            false)))));
    assertNoInvoiceWrites();
  }

  @Test
  void shouldRequireTheResolvedBusinessPartnerToHaveTheCustomerRole() {
    registerPartner("vendor-1", BusinessPartnerRole.VENDOR);

    assertThrows(
        InvoiceCustomerRoleRequiredException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "invoice-1",
                            "v1",
                            SOURCE_TIME,
                            "vendor-1",
                            "100.00",
                            "0.00",
                            false)))));
    assertNoInvoiceWrites();
  }

  @Test
  void shouldFailWhenAnAcceptedMappingReferencesAMissingInvoice() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    InvoiceImportRecord existing =
        record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false);
    sourceMappingRepository.save(
        InvoiceSourceMapping.create(
            tenantId(),
            sourceSystemId(),
            existing.sourceIdentity(),
            InvoiceId.generate(),
            existing.sourceVersion(),
            existing.sourceModifiedAt(),
            InvoiceImportFingerprint.record(existing)));
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        InvoiceSourceMappingCorruptedException.class,
        () ->
            service.importPage(
                command(
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "invoice-1",
                            "v2",
                            SOURCE_TIME.plusSeconds(60),
                            "customer-1",
                            "100.00",
                            "0.00",
                            false)))));
    assertEquals(0, invoiceRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertTrue(receiptRepository.values.isEmpty());
  }

  @Test
  void shouldRejectEqualVersionPayloadConflictsWithoutMutatingState() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(
                record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false))));
    int invoiceSaves = invoiceRepository.saveCount;
    int mappingSaves = sourceMappingRepository.saveCount;

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () ->
            service.importPage(
                command(
                    UUID.randomUUID(),
                    List.of(
                        record(
                            "invoice-1",
                            "v1",
                            SOURCE_TIME,
                            "customer-1",
                            "120.00",
                            "0.00",
                            false)))));
    assertEquals(invoiceSaves, invoiceRepository.saveCount);
    assertEquals(mappingSaves, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.values.size());
    assertEquals(Money.of("100.00", CurrencyCode.of("EUR")), originalInvoice().originalAmount());
  }

  @Test
  void shouldRejectDifferentOpaqueVersionsWithoutComparableTimestamps() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    service.importPage(
        command(
            PAGE_ACCEPTANCE_ID,
            List.of(record("invoice-1", "v1", null, "customer-1", "100.00", "0.00", false))));

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () ->
            service.importPage(
                command(
                    UUID.randomUUID(),
                    List.of(
                        record("invoice-1", "v2", null, "customer-1", "100.00", "0.00", false)))));
    assertEquals(1, invoiceRepository.saveCount);
    assertEquals(1, sourceMappingRepository.saveCount);
    assertEquals(1, receiptRepository.saveCount);
  }

  @Test
  void shouldEvaluateRepeatedSourceIdentitiesAgainstEarlierRecordsInTheSamePage() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    InvoiceImportRecord first =
        record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false);
    InvoiceImportRecord second =
        record(
            "invoice-1", "v2", SOURCE_TIME.plusSeconds(60), "customer-1", "120.00", "20.00", false);

    InvoiceImportResult result =
        service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(first, second)));

    assertEquals(2, result.fetchedCount());
    assertEquals(1, result.createdCount());
    assertEquals(1, result.updatedCount());
    assertEquals(1, invoiceRepository.saveCount);
    assertEquals(1, sourceMappingRepository.saveCount);
    assertEquals(Money.of("120.00", CurrencyCode.of("EUR")), originalInvoice().originalAmount());
    assertEquals(
        new SourceRecordVersion("v2"),
        sourceMappingRepository.values.values().iterator().next().sourceVersion());
  }

  @Test
  void shouldCompleteDeterministicPreflightBeforeWritingAnyRecord() {
    registerPartner("customer-1", BusinessPartnerRole.CUSTOMER);
    InvoiceImportRecord valid =
        record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false);
    InvoiceImportRecord invalid =
        record("invoice-2", "v1", SOURCE_TIME, "customer-1", "50.00", "60.00", false);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.importPage(command(PAGE_ACCEPTANCE_ID, List.of(valid, invalid))));
    assertNoInvoiceWrites();
  }

  @Test
  void shouldAcceptAndReceiptAnEmptyPage() {
    InvoiceImportResult result = service.importPage(command(PAGE_ACCEPTANCE_ID, List.of()));

    assertEquals(0, result.fetchedCount());
    assertEquals(0, result.acceptedCount());
    assertEquals(0, result.notAppliedCount());
    assertTrue(invoiceRepository.values.isEmpty());
    assertTrue(sourceMappingRepository.values.isEmpty());
    assertEquals(1, receiptRepository.values.size());
  }

  @Test
  void shouldDefensivelyCopyAndValidateImportContracts() {
    InvoiceImportRecord record =
        record("invoice-1", "v1", SOURCE_TIME, "customer-1", "100.00", "0.00", false);
    List<InvoiceImportRecord> records = new ArrayList<>();
    records.add(record);
    ImportInvoicesCommand command = command(PAGE_ACCEPTANCE_ID, records);

    records.clear();

    assertEquals(1, command.records().size());
    assertThrows(UnsupportedOperationException.class, () -> command.records().clear());
    assertThrows(
        NullPointerException.class,
        () -> command(PAGE_ACCEPTANCE_ID, Arrays.asList((InvoiceImportRecord) null)));
    assertThrows(NullPointerException.class, () -> service.importPage(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvoiceImportResult(PAGE_ACCEPTANCE_ID, 2, 1, 0, 0, 0, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvoiceImportResult(PAGE_ACCEPTANCE_ID, -1, 0, 0, 0, 0, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ImportInvoicesCommand(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                Collections.nCopies(ImportInvoicesCommand.MAX_RECORDS_PER_PAGE + 1, record)));
    InvoiceImportResult validResult =
        new InvoiceImportResult(PAGE_ACCEPTANCE_ID, 0, 0, 0, 0, 0, NOW);
    assertThrows(
        IllegalArgumentException.class,
        () -> new InvoiceImportReceipt("not-a-sha-256-fingerprint", validResult));
  }

  private BusinessPartner registerPartner(String sourceIdentity, BusinessPartnerRole... roles) {
    BusinessPartner partner =
        BusinessPartner.reconstitute(
            BusinessPartnerId.generate(),
            tenantId(),
            new BusinessPartnerProfile("BP-" + sourceIdentity, sourceIdentity, Optional.empty()),
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

  private Invoice originalInvoice() {
    return invoiceRepository.values.values().iterator().next();
  }

  private void assertNoInvoiceWrites() {
    assertEquals(0, invoiceRepository.saveCount);
    assertEquals(0, sourceMappingRepository.saveCount);
    assertTrue(receiptRepository.values.isEmpty());
  }

  private static OperationsTenantId tenantId() {
    return OperationsTenantId.of(TENANT_ID);
  }

  private static SourceSystemId sourceSystemId() {
    return SourceSystemId.of(SOURCE_SYSTEM_ID);
  }

  private static ImportInvoicesCommand command(
      UUID pageAcceptanceId, List<InvoiceImportRecord> records) {
    return new ImportInvoicesCommand(
        TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, pageAcceptanceId, records);
  }

  private static InvoiceImportRecord record(
      String sourceIdentity,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerIdentity,
      String originalAmount,
      String paidAmount,
      boolean cancelled) {
    return new InvoiceImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(sourceVersion),
        Optional.ofNullable(sourceModifiedAt),
        SourceRecordIdentity.sourceRecordId(customerIdentity),
        new InvoiceNumber("INV-" + sourceIdentity),
        Money.of(originalAmount, CurrencyCode.of("EUR")),
        Money.of(paidAmount, CurrencyCode.of("EUR")),
        LocalDate.parse("2026-08-01"),
        LocalDate.parse("2026-09-01"),
        cancelled);
  }

  private record InvoiceKey(OperationsTenantId tenantId, InvoiceId invoiceId) {}

  private static final class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Map<InvoiceKey, Invoice> values = new HashMap<>();
    private int saveCount;

    @Override
    public Invoice save(Invoice invoice) {
      values.put(new InvoiceKey(invoice.tenantId(), invoice.id()), invoice);
      saveCount++;
      return invoice;
    }

    @Override
    public Optional<Invoice> findById(OperationsTenantId tenantId, InvoiceId invoiceId) {
      return Optional.ofNullable(values.get(new InvoiceKey(tenantId, invoiceId)));
    }
  }

  private record InvoiceSourceKey(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {}

  private static final class InMemoryInvoiceSourceMappingRepository
      implements InvoiceSourceMappingRepository {

    private final Map<InvoiceSourceKey, InvoiceSourceMapping> values = new HashMap<>();
    private int saveCount;

    @Override
    public InvoiceSourceMapping save(InvoiceSourceMapping sourceMapping) {
      values.put(
          new InvoiceSourceKey(
              sourceMapping.tenantId(),
              sourceMapping.sourceSystemId(),
              sourceMapping.sourceIdentity()),
          sourceMapping);
      saveCount++;
      return sourceMapping;
    }

    @Override
    public Optional<InvoiceSourceMapping> findBySourceIdentity(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        SourceRecordIdentity sourceIdentity) {
      return Optional.ofNullable(
          values.get(new InvoiceSourceKey(tenantId, sourceSystemId, sourceIdentity)));
    }
  }

  private record ReceiptKey(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId) {}

  private static final class InMemoryInvoiceImportReceiptRepository
      implements InvoiceImportReceiptRepository {

    private final Map<ReceiptKey, InvoiceImportReceipt> values = new HashMap<>();
    private int saveCount;

    @Override
    public Optional<InvoiceImportReceipt> find(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        UUID pageAcceptanceId) {
      return Optional.ofNullable(
          values.get(new ReceiptKey(tenantId, sourceSystemId, importBatchId, pageAcceptanceId)));
    }

    @Override
    public InvoiceImportReceipt save(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        InvoiceImportReceipt receipt) {
      values.put(
          new ReceiptKey(
              tenantId, sourceSystemId, importBatchId, receipt.result().pageAcceptanceId()),
          receipt);
      saveCount++;
      return receipt;
    }
  }

  private record PartnerKey(OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {}

  private static final class InMemoryBusinessPartnerRepository
      implements BusinessPartnerRepository {

    private final Map<PartnerKey, BusinessPartner> values = new HashMap<>();

    @Override
    public BusinessPartner save(BusinessPartner businessPartner) {
      values.put(new PartnerKey(businessPartner.tenantId(), businessPartner.id()), businessPartner);
      return businessPartner;
    }

    @Override
    public Optional<BusinessPartner> findById(
        OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {
      return Optional.ofNullable(values.get(new PartnerKey(tenantId, businessPartnerId)));
    }
  }

  private record PartnerSourceKey(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {}

  private static final class InMemoryBusinessPartnerSourceMappingRepository
      implements BusinessPartnerSourceMappingRepository {

    private final Map<PartnerSourceKey, BusinessPartnerSourceMapping> values = new HashMap<>();

    @Override
    public BusinessPartnerSourceMapping save(BusinessPartnerSourceMapping sourceMapping) {
      values.put(
          new PartnerSourceKey(
              sourceMapping.tenantId(),
              sourceMapping.sourceSystemId(),
              sourceMapping.sourceIdentity()),
          sourceMapping);
      return sourceMapping;
    }

    @Override
    public Optional<BusinessPartnerSourceMapping> findBySourceIdentity(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        SourceRecordIdentity sourceIdentity) {
      return Optional.ofNullable(
          values.get(new PartnerSourceKey(tenantId, sourceSystemId, sourceIdentity)));
    }
  }

  private static final class DirectInvoiceImportUnitOfWork implements InvoiceImportUnitOfWork {

    private int executionCount;

    @Override
    public InvoiceImportResult execute(Supplier<InvoiceImportResult> work) {
      executionCount++;
      return work.get();
    }
  }
}
