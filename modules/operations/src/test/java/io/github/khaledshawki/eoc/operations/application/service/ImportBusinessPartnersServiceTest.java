package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.model.importing.BusinessPartnerImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportBusinessPartnersServiceTest {

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
  private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");

  private InMemoryBusinessPartnerRepository businessPartnerRepository;
  private InMemorySourceMappingRepository sourceMappingRepository;
  private InMemoryImportReceiptRepository importReceiptRepository;
  private ImportBusinessPartnersService service;

  @BeforeEach
  void setUp() {
    businessPartnerRepository = new InMemoryBusinessPartnerRepository();
    sourceMappingRepository = new InMemorySourceMappingRepository();
    importReceiptRepository = new InMemoryImportReceiptRepository();
    service =
        new ImportBusinessPartnersService(
            businessPartnerRepository,
            sourceMappingRepository,
            importReceiptRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void shouldCreateCustomersAndPersistOnePageReceipt() {
    ImportBusinessPartnersCommand command =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme")));

    BusinessPartnerImportResult result = service.importPage(command);

    assertEquals(1, result.fetchedCount());
    assertEquals(1, result.createdCount());
    assertEquals(0, result.updatedCount());
    assertEquals(0, result.duplicateCount());
    assertEquals(0, result.staleCount());
    assertEquals(1, result.acceptedCount());
    assertEquals(NOW, result.acceptedAt());
    assertEquals(1, businessPartnerRepository.values.size());
    assertEquals(1, sourceMappingRepository.values.size());
    assertEquals(1, importReceiptRepository.values.size());
  }

  @Test
  void shouldUpdateARecordOnlyWhenItsSourceVersionIsNewer() {
    service.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme"))));
    UUID secondAcceptanceId = UUID.randomUUID();
    BusinessPartnerImportResult result =
        service.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                secondAcceptanceId,
                List.of(
                    record("customer-1", "v2", SOURCE_TIME.plusSeconds(60), "C-1", "Acme AG"))));

    assertEquals(0, result.createdCount());
    assertEquals(1, result.updatedCount());
    BusinessPartnerSourceMapping mapping =
        sourceMappingRepository.values.values().iterator().next();
    BusinessPartner businessPartner =
        businessPartnerRepository
            .findById(OperationsTenantId.of(TENANT_ID), mapping.businessPartnerId())
            .orElseThrow();
    assertEquals("Acme AG", businessPartner.profile().displayName());
    assertEquals(new SourceRecordVersion("v2"), mapping.sourceVersion());
  }

  @Test
  void shouldClassifyRepeatedAndStaleRecordsWithoutMutatingThePartner() {
    BusinessPartnerImportRecord current =
        record("customer-1", "v2", SOURCE_TIME.plusSeconds(60), "C-1", "Current name");
    service.importPage(
        command(
            TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of(current)));
    BusinessPartnerImportResult result =
        service.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(current, record("customer-1", "v1", SOURCE_TIME, "C-1", "Stale name"))));

    assertEquals(2, result.fetchedCount());
    assertEquals(1, result.duplicateCount());
    assertEquals(1, result.staleCount());
    assertEquals(0, result.acceptedCount());
    BusinessPartner businessPartner = businessPartnerRepository.values.values().iterator().next();
    assertEquals("Current name", businessPartner.profile().displayName());
  }

  @Test
  void shouldReturnTheOriginalReceiptWhenTheSamePageIsRetried() {
    ImportBusinessPartnersCommand command =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme")));
    BusinessPartnerImportResult first = service.importPage(command);
    int partnerSaveCount = businessPartnerRepository.saveCount;
    int mappingSaveCount = sourceMappingRepository.saveCount;

    BusinessPartnerImportResult replay = service.importPage(command);

    assertSame(first, replay);
    assertEquals(partnerSaveCount, businessPartnerRepository.saveCount);
    assertEquals(mappingSaveCount, sourceMappingRepository.saveCount);
    assertEquals(1, importReceiptRepository.saveCount);
  }

  @Test
  void shouldRejectAcceptanceIdReuseWithDifferentPayload() {
    ImportBusinessPartnersCommand original =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme")));
    service.importPage(original);
    int partnerSaveCount = businessPartnerRepository.saveCount;
    int mappingSaveCount = sourceMappingRepository.saveCount;

    ImportBusinessPartnersCommand conflicting =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("different-record", "v1", SOURCE_TIME, "X", "Conflict")));

    assertThrows(
        ImportPageAcceptanceConflictException.class, () -> service.importPage(conflicting));
    assertEquals(partnerSaveCount, businessPartnerRepository.saveCount);
    assertEquals(mappingSaveCount, sourceMappingRepository.saveCount);
    assertEquals(1, importReceiptRepository.saveCount);
  }

  @Test
  void shouldScopeReceiptsAndMappingsByTenantSourceBatchAndAcceptance() {
    service.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("shared-id", "v1", SOURCE_TIME, "C-1", "First"))));

    BusinessPartnerImportResult otherTenant =
        service.importPage(
            command(
                OTHER_TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared-id", "v1", SOURCE_TIME, "C-1", "Other tenant"))));
    BusinessPartnerImportResult otherSource =
        service.importPage(
            command(
                TENANT_ID,
                OTHER_SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared-id", "v1", SOURCE_TIME, "C-1", "Other source"))));
    BusinessPartnerImportResult otherBatch =
        service.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                UUID.randomUUID(),
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared-id", "v1", SOURCE_TIME, "C-1", "Same source"))));

    assertEquals(1, otherTenant.createdCount());
    assertEquals(1, otherSource.createdCount());
    assertEquals(1, otherBatch.duplicateCount());
    assertEquals(3, businessPartnerRepository.values.size());
    assertEquals(3, sourceMappingRepository.values.size());
    assertEquals(4, importReceiptRepository.values.size());
  }

  @Test
  void shouldAcceptAndReceiptAnEmptyPage() {
    BusinessPartnerImportResult result =
        service.importPage(
            command(TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of()));

    assertEquals(0, result.fetchedCount());
    assertEquals(0, result.acceptedCount());
    assertEquals(0, result.notAppliedCount());
    assertTrue(businessPartnerRepository.values.isEmpty());
    assertEquals(1, importReceiptRepository.values.size());
  }

  @Test
  void shouldFailWhenASourceMappingReferencesAMissingPartner() {
    BusinessPartnerSourceMapping corruptedMapping =
        BusinessPartnerSourceMapping.create(
            OperationsTenantId.of(TENANT_ID),
            SourceSystemId.of(SOURCE_SYSTEM_ID),
            SourceRecordIdentity.sourceRecordId("customer-1"),
            BusinessPartnerId.generate(),
            new SourceRecordVersion("v1"),
            Optional.of(SOURCE_TIME));
    sourceMappingRepository.save(corruptedMapping);

    assertThrows(
        BusinessPartnerSourceMappingCorruptedException.class,
        () ->
            service.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "customer-1", "v2", SOURCE_TIME.plusSeconds(60), "C-1", "Acme AG")))));
    assertTrue(importReceiptRepository.values.isEmpty());
  }

  @Test
  void shouldRejectAmbiguousVersionOrderingWithoutWritingAReceipt() {
    service.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme"))));

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () ->
            service.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    UUID.randomUUID(),
                    List.of(record("customer-1", "v2", SOURCE_TIME, "C-1", "Conflict")))));
    assertEquals(1, importReceiptRepository.values.size());
  }

  @Test
  void shouldDefensivelyCopyAndValidatePageRecords() {
    List<BusinessPartnerImportRecord> records = new ArrayList<>();
    records.add(record("customer-1", "v1", SOURCE_TIME, "C-1", "Acme"));
    ImportBusinessPartnersCommand command =
        command(TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, records);

    records.clear();

    assertEquals(1, command.records().size());
    assertThrows(UnsupportedOperationException.class, () -> command.records().clear());
    assertThrows(
        NullPointerException.class,
        () ->
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                java.util.Arrays.asList((BusinessPartnerImportRecord) null)));
    assertThrows(NullPointerException.class, () -> service.importPage(null));
  }

  @Test
  void shouldValidateResultClassificationInvariant() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BusinessPartnerImportResult(PAGE_ACCEPTANCE_ID, 2, 1, 0, 0, 0, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BusinessPartnerImportResult(PAGE_ACCEPTANCE_ID, -1, 0, 0, 0, 0, NOW));
  }

  private static ImportBusinessPartnersCommand command(
      UUID tenantId,
      UUID sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId,
      List<BusinessPartnerImportRecord> records) {
    return new ImportBusinessPartnersCommand(
        tenantId, sourceSystemId, importBatchId, pageAcceptanceId, records);
  }

  private static BusinessPartnerImportRecord record(
      String sourceRecordId,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerNumber,
      String displayName) {
    return new BusinessPartnerImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceRecordId),
        new SourceRecordVersion(sourceVersion),
        Optional.ofNullable(sourceModifiedAt),
        new BusinessPartnerProfile(customerNumber, displayName, Optional.empty()));
  }

  private record PartnerKey(OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {}

  private static final class InMemoryBusinessPartnerRepository
      implements BusinessPartnerRepository {

    private final Map<PartnerKey, BusinessPartner> values = new HashMap<>();
    private int saveCount;

    @Override
    public BusinessPartner save(BusinessPartner businessPartner) {
      values.put(new PartnerKey(businessPartner.tenantId(), businessPartner.id()), businessPartner);
      saveCount++;
      return businessPartner;
    }

    @Override
    public Optional<BusinessPartner> findById(
        OperationsTenantId tenantId, BusinessPartnerId businessPartnerId) {
      return Optional.ofNullable(values.get(new PartnerKey(tenantId, businessPartnerId)));
    }
  }

  private record SourceKey(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {}

  private static final class InMemorySourceMappingRepository
      implements BusinessPartnerSourceMappingRepository {

    private final Map<SourceKey, BusinessPartnerSourceMapping> values = new HashMap<>();
    private int saveCount;

    @Override
    public BusinessPartnerSourceMapping save(BusinessPartnerSourceMapping sourceMapping) {
      values.put(
          new SourceKey(
              sourceMapping.tenantId(),
              sourceMapping.sourceSystemId(),
              sourceMapping.sourceIdentity()),
          sourceMapping);
      saveCount++;
      return sourceMapping;
    }

    @Override
    public Optional<BusinessPartnerSourceMapping> findBySourceIdentity(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        SourceRecordIdentity sourceIdentity) {
      return Optional.ofNullable(
          values.get(new SourceKey(tenantId, sourceSystemId, sourceIdentity)));
    }
  }

  private record ReceiptKey(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId) {}

  private static final class InMemoryImportReceiptRepository
      implements BusinessPartnerImportReceiptRepository {

    private final Map<ReceiptKey, BusinessPartnerImportReceipt> values = new HashMap<>();
    private int saveCount;

    @Override
    public Optional<BusinessPartnerImportReceipt> find(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        UUID pageAcceptanceId) {
      return Optional.ofNullable(
          values.get(new ReceiptKey(tenantId, sourceSystemId, importBatchId, pageAcceptanceId)));
    }

    @Override
    public BusinessPartnerImportReceipt save(
        OperationsTenantId tenantId,
        SourceSystemId sourceSystemId,
        UUID importBatchId,
        BusinessPartnerImportReceipt receipt) {
      ReceiptKey key =
          new ReceiptKey(
              tenantId, sourceSystemId, importBatchId, receipt.result().pageAcceptanceId());
      assertFalse(values.containsKey(key));
      values.put(key, Objects.requireNonNull(receipt));
      saveCount++;
      return receipt;
    }
  }
}
