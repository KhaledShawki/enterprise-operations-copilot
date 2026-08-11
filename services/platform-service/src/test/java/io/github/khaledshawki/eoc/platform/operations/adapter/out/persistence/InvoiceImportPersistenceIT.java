package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordReplayException;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
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
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  InvoiceImportPersistenceIT.MutableClockConfiguration.class
})
class InvoiceImportPersistenceIT {

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
  private static final Instant FIRST_IMPORT_TIME = Instant.parse("2026-08-06T08:00:00Z");
  private static final Instant SECOND_IMPORT_TIME = Instant.parse("2026-08-06T09:00:00Z");
  private static final Instant FIRST_SOURCE_TIME = Instant.parse("2026-08-05T08:00:00Z");
  private static final Instant SECOND_SOURCE_TIME = Instant.parse("2026-08-05T09:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Autowired private ImportBusinessPartnersUseCase importBusinessPartnersUseCase;
  @Autowired private ImportInvoicesUseCase importInvoicesUseCase;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceSourceMappingRepository sourceMappingRepository;
  @Autowired private InvoiceImportUnitOfWork unitOfWork;
  @Autowired private SpringDataInvoiceRepository springDataInvoiceRepository;
  @Autowired private SpringDataInvoiceSourceMappingRepository springDataSourceMappingRepository;
  @Autowired private SpringDataInvoiceImportReceiptRepository springDataReceiptRepository;
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
    springDataInvoiceRepository.deleteAllInBatch();
    springDataBusinessPartnerReceiptRepository.deleteAllInBatch();
    springDataBusinessPartnerMappingRepository.deleteAllInBatch();
    springDataBusinessPartnerRepository.deleteAllInBatch();
    clock.setInstant(FIRST_IMPORT_TIME);
  }

  @Test
  void shouldPersistInvoiceSourceMappingAndReceiptWithTenantIsolation() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    InvoiceImportResult result =
        importInvoicesUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record(
                        "invoice-1",
                        "v1",
                        FIRST_SOURCE_TIME,
                        "customer-1",
                        "INV-1",
                        "120.00",
                        "20.00",
                        false))));

    assertEquals(1, result.createdCount());
    assertEquals(FIRST_IMPORT_TIME, result.acceptedAt());
    assertEquals(1, springDataInvoiceRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
    assertEquals(1L, eventCount("operations.invoice.synchronized.v1"));

    InvoiceSourceMapping mapping = mapping(TENANT_ID, SOURCE_SYSTEM_ID, "invoice-1");
    Invoice invoice =
        invoiceRepository
            .findById(OperationsTenantId.of(TENANT_ID), mapping.invoiceId())
            .orElseThrow();
    assertEquals(new InvoiceNumber("INV-1"), invoice.invoiceNumber());
    assertEquals(Money.of("120.00", EUR), invoice.originalAmount());
    assertEquals(Money.of("20.00", EUR), invoice.paidAmount());
    assertEquals(Money.of("100.00", EUR), invoice.openAmount());
    assertEquals(64, mapping.payloadFingerprint().value().length());
    assertTrue(
        invoiceRepository.findById(OperationsTenantId.of(OTHER_TENANT_ID), invoice.id()).isEmpty());
  }

  @Test
  void shouldReplayTheOriginalReceiptWithoutMutatingPersistedState() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    ImportInvoicesCommand original =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "invoice-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "INV-1",
                    "120.00",
                    "20.00",
                    false)));
    InvoiceImportResult first = importInvoicesUseCase.importPage(original);
    InvoiceSourceMappingJpaEntity mappingBefore = mappingEntity("invoice-1");
    InvoiceJpaEntity invoiceBefore =
        springDataInvoiceRepository.findById(mappingBefore.getInvoiceId()).orElseThrow();
    clock.setInstant(SECOND_IMPORT_TIME);

    InvoiceImportResult replay = importInvoicesUseCase.importPage(original);

    assertEquals(first, replay);
    InvoiceSourceMappingJpaEntity mappingAfter = mappingEntity("invoice-1");
    InvoiceJpaEntity invoiceAfter =
        springDataInvoiceRepository.findById(mappingAfter.getInvoiceId()).orElseThrow();
    assertEquals(mappingBefore.getVersion(), mappingAfter.getVersion());
    assertEquals(invoiceBefore.getVersion(), invoiceAfter.getVersion());
    assertEquals(FIRST_IMPORT_TIME, mappingAfter.getUpdatedAt());
    assertEquals(FIRST_IMPORT_TIME, invoiceAfter.getUpdatedAt());
    assertEquals(1L, eventCount("operations.invoice.synchronized.v1"));
  }

  @Test
  void shouldPersistNewerEvidenceAndIgnoreStaleEvidence() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importInvoicesUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "invoice-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "INV-1",
                    "120.00",
                    "0.00",
                    false))));
    clock.setInstant(SECOND_IMPORT_TIME);

    InvoiceImportResult updated =
        importInvoicesUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "invoice-1",
                        "v2",
                        SECOND_SOURCE_TIME,
                        "customer-1",
                        "INV-1",
                        "120.00",
                        "50.00",
                        false))));
    InvoiceImportResult stale =
        importInvoicesUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "invoice-1",
                        "v0",
                        FIRST_SOURCE_TIME.minusSeconds(1),
                        "customer-1",
                        "INV-1",
                        "120.00",
                        "10.00",
                        false))));

    assertEquals(1, updated.updatedCount());
    assertEquals(1, stale.staleCount());
    InvoiceSourceMappingJpaEntity mapping = mappingEntity("invoice-1");
    InvoiceJpaEntity invoice =
        springDataInvoiceRepository.findById(mapping.getInvoiceId()).orElseThrow();
    assertEquals("v2", mapping.getSourceVersion());
    assertEquals(Long.valueOf(1L), mapping.getVersion());
    assertEquals(0, invoice.getPaidAmount().compareTo(Money.of("50.00", EUR).amount()));
    assertEquals(Long.valueOf(1L), invoice.getVersion());
    assertEquals(SECOND_IMPORT_TIME, mapping.getUpdatedAt());
    assertEquals(SECOND_IMPORT_TIME, invoice.getUpdatedAt());
  }

  @Test
  void shouldRejectChangedPageReplayWithoutMutation() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importInvoicesUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "invoice-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "INV-1",
                    "120.00",
                    "0.00",
                    false))));

    ImportInvoicesCommand conflict =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(
                record(
                    "invoice-2",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "INV-2",
                    "10.00",
                    "0.00",
                    false)));

    assertThrows(
        ImportPageAcceptanceConflictException.class,
        () -> importInvoicesUseCase.importPage(conflict));
    assertEquals(1, springDataInvoiceRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
  }

  @Test
  void shouldRollBackTheUnitOfWorkWhenPersistenceFailsAfterAnInvoiceWrite() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    var customerMapping =
        springDataBusinessPartnerMappingRepository.findAll().stream().findFirst().orElseThrow();
    Invoice invoice =
        Invoice.importCustomerInvoice(
            OperationsTenantId.of(TENANT_ID),
            io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId.of(
                customerMapping.getBusinessPartnerId()),
            new InvoiceNumber("INV-ROLLBACK"),
            Money.of("10.00", EUR),
            Money.zero(EUR),
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            false);

    assertThrows(
        IllegalStateException.class,
        () ->
            unitOfWork.execute(
                () -> {
                  invoiceRepository.save(invoice);
                  throw new IllegalStateException("force rollback");
                }));

    assertEquals(0, springDataInvoiceRepository.count());
  }

  @Test
  void shouldRejectMissingCustomerAndConflictingReplayWithoutPartialWrites() {
    assertThrows(
        InvoiceCustomerSourceMappingNotFoundException.class,
        () ->
            importInvoicesUseCase.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    PAGE_ACCEPTANCE_ID,
                    List.of(
                        record(
                            "missing-customer-invoice",
                            "v1",
                            FIRST_SOURCE_TIME,
                            "missing-customer",
                            "INV-MISSING",
                            "10.00",
                            "0.00",
                            false)))));
    assertEquals(0, springDataInvoiceRepository.count());
    assertEquals(0, springDataReceiptRepository.count());

    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    importInvoicesUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            UUID.randomUUID(),
            List.of(
                record(
                    "invoice-1",
                    "v1",
                    FIRST_SOURCE_TIME,
                    "customer-1",
                    "INV-1",
                    "10.00",
                    "0.00",
                    false))));
    long invoiceCount = springDataInvoiceRepository.count();
    long mappingCount = springDataSourceMappingRepository.count();

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () ->
            importInvoicesUseCase.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    UUID.randomUUID(),
                    List.of(
                        record(
                            "new-invoice",
                            "v1",
                            SECOND_SOURCE_TIME,
                            "customer-1",
                            "INV-NEW",
                            "5.00",
                            "0.00",
                            false),
                        record(
                            "invoice-1",
                            "v1",
                            FIRST_SOURCE_TIME,
                            "customer-1",
                            "INV-1",
                            "11.00",
                            "0.00",
                            false)))));

    assertEquals(invoiceCount, springDataInvoiceRepository.count());
    assertEquals(mappingCount, springDataSourceMappingRepository.count());
    assertTrue(
        sourceMappingRepository
            .findBySourceIdentity(
                OperationsTenantId.of(TENANT_ID),
                SourceSystemId.of(SOURCE_SYSTEM_ID),
                SourceRecordIdentity.sourceRecordId("new-invoice"))
            .isEmpty());
  }

  @Test
  void shouldScopeInvoiceMappingsAndReceiptsByTenantAndSource() {
    seedCustomer(TENANT_ID, SOURCE_SYSTEM_ID, "customer");
    seedCustomer(OTHER_TENANT_ID, SOURCE_SYSTEM_ID, "customer");
    seedCustomer(TENANT_ID, OTHER_SOURCE_SYSTEM_ID, "customer");

    for (ImportInvoicesCommand command :
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
                        "INV-1",
                        "10.00",
                        "0.00",
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
                        "INV-2",
                        "20.00",
                        "0.00",
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
                        "INV-3",
                        "30.00",
                        "0.00",
                        false))))) {
      assertEquals(1, importInvoicesUseCase.importPage(command).createdCount());
    }

    assertEquals(3, springDataInvoiceRepository.count());
    assertEquals(3, springDataSourceMappingRepository.count());
    assertEquals(3, springDataReceiptRepository.count());
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
                    Optional.of(FIRST_SOURCE_TIME),
                    new BusinessPartnerProfile(
                        "C-" + sourceIdentity, "Customer " + sourceIdentity, Optional.empty())))));
  }

  private InvoiceSourceMapping mapping(UUID tenantId, UUID sourceSystemId, String sourceIdentity) {
    return sourceMappingRepository
        .findBySourceIdentity(
            OperationsTenantId.of(tenantId),
            SourceSystemId.of(sourceSystemId),
            SourceRecordIdentity.sourceRecordId(sourceIdentity))
        .orElseThrow();
  }

  private InvoiceSourceMappingJpaEntity mappingEntity(String sourceIdentity) {
    return springDataSourceMappingRepository
        .findById(
            new InvoiceSourceMappingJpaId(
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

  private static ImportInvoicesCommand command(
      UUID tenantId,
      UUID sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId,
      List<InvoiceImportRecord> records) {
    return new ImportInvoicesCommand(
        tenantId, sourceSystemId, importBatchId, pageAcceptanceId, records);
  }

  private static InvoiceImportRecord record(
      String sourceIdentity,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerSourceIdentity,
      String invoiceNumber,
      String originalAmount,
      String paidAmount,
      boolean cancelled) {
    return new InvoiceImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion(sourceVersion),
        Optional.ofNullable(sourceModifiedAt),
        SourceRecordIdentity.sourceRecordId(customerSourceIdentity),
        new InvoiceNumber(invoiceNumber),
        Money.of(originalAmount, EUR),
        Money.of(paidAmount, EUR),
        LocalDate.parse("2026-08-01"),
        LocalDate.parse("2026-08-31"),
        cancelled);
  }

  static final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
    }

    void setInstant(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock operationsInvoiceImportTestClock() {
      return new MutableClock(FIRST_IMPORT_TIME);
    }
  }
}
