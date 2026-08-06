package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvoiceQueryPersistenceIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-05T08:00:00Z");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 6);
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Autowired private ImportBusinessPartnersUseCase importBusinessPartnersUseCase;
  @Autowired private ImportInvoicesUseCase importInvoicesUseCase;
  @Autowired private BusinessPartnerSourceMappingRepository businessPartnerMappingRepository;
  @Autowired private InvoiceQueryRepository invoiceQueryRepository;
  @Autowired private SpringDataInvoiceRepository springDataInvoiceRepository;
  @Autowired private SpringDataInvoiceSourceMappingRepository springDataInvoiceMappingRepository;
  @Autowired private SpringDataInvoiceImportReceiptRepository springDataInvoiceReceiptRepository;
  @Autowired private SpringDataBusinessPartnerRepository springDataBusinessPartnerRepository;

  @Autowired
  private SpringDataBusinessPartnerSourceMappingRepository
      springDataBusinessPartnerMappingRepository;

  @Autowired
  private SpringDataBusinessPartnerImportReceiptRepository
      springDataBusinessPartnerReceiptRepository;

  @BeforeEach
  void setUp() {
    springDataInvoiceReceiptRepository.deleteAllInBatch();
    springDataInvoiceMappingRepository.deleteAllInBatch();
    springDataInvoiceRepository.deleteAllInBatch();
    springDataBusinessPartnerReceiptRepository.deleteAllInBatch();
    springDataBusinessPartnerMappingRepository.deleteAllInBatch();
    springDataBusinessPartnerRepository.deleteAllInBatch();
  }

  @Test
  void shouldApplyTenantIsolationAndDeterministicDueDatePagination() {
    seedCustomer(TENANT_ID, "customer-a");
    seedInvoices(
        TENANT_ID,
        "customer-a",
        List.of(
            record("invoice-3", "INV-3", "100.00", "0.00", "2026-07-03", "2026-08-10", false),
            record("invoice-1", "INV-1", "100.00", "0.00", "2026-07-01", "2026-08-01", false),
            record("invoice-2", "INV-2", "100.00", "25.00", "2026-07-02", "2026-08-06", false)));
    seedCustomer(OTHER_TENANT_ID, "customer-a");
    seedInvoices(
        OTHER_TENANT_ID,
        "customer-a",
        List.of(
            record(
                "other-invoice", "OTHER-1", "100.00", "0.00", "2026-07-01", "2026-07-01", false)));

    InvoiceQueryPage first = invoiceQueryRepository.findPage(criteria(0, 2));
    InvoiceQueryPage second = invoiceQueryRepository.findPage(criteria(1, 2));

    assertEquals(3, first.totalElements());
    assertEquals(List.of("INV-1", "INV-2"), numbers(first));
    assertEquals(List.of("INV-3"), numbers(second));
    assertTrue(first.hasNext());
  }

  @Test
  void shouldFilterByCanonicalLifecycleStatus() {
    seedCustomer(TENANT_ID, "customer-a");
    seedInvoices(
        TENANT_ID,
        "customer-a",
        List.of(
            record("open", "OPEN-1", "100.00", "0.00", "2026-07-01", "2026-08-10", false),
            record("partial", "PARTIAL-1", "100.00", "25.00", "2026-07-02", "2026-08-10", false),
            record("paid", "PAID-1", "100.00", "100.00", "2026-07-03", "2026-08-10", false),
            record(
                "cancelled", "CANCELLED-1", "100.00", "0.00", "2026-07-04", "2026-08-10", true)));

    InvoiceQueryPage page =
        invoiceQueryRepository.findPage(
            new InvoiceQueryCriteria(
                OperationsTenantId.of(TENANT_ID),
                Optional.empty(),
                Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                50,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.ASC));

    assertEquals(List.of("OPEN-1", "PARTIAL-1"), numbers(page));
  }

  @Test
  void shouldFilterDueStatesWithoutTreatingPaidOrCancelledInvoicesAsOverdue() {
    seedCustomer(TENANT_ID, "customer-a");
    seedInvoices(
        TENANT_ID,
        "customer-a",
        List.of(
            record("overdue", "OVERDUE-1", "100.00", "0.00", "2026-07-01", "2026-08-01", false),
            record("today", "TODAY-1", "100.00", "0.00", "2026-07-02", "2026-08-06", false),
            record("future", "FUTURE-1", "100.00", "0.00", "2026-07-03", "2026-08-10", false),
            record("paid", "PAID-1", "100.00", "100.00", "2026-07-04", "2026-08-01", false),
            record(
                "cancelled", "CANCELLED-1", "100.00", "0.00", "2026-07-05", "2026-08-01", true)));

    assertEquals(List.of("OVERDUE-1"), numbers(dueState(InvoiceDueState.OVERDUE)));
    assertEquals(List.of("TODAY-1"), numbers(dueState(InvoiceDueState.DUE_TODAY)));
    assertEquals(List.of("FUTURE-1"), numbers(dueState(InvoiceDueState.NOT_DUE)));
    assertEquals(
        Set.of("PAID-1", "CANCELLED-1"), Set.copyOf(numbers(dueState(InvoiceDueState.SETTLED))));
  }

  @Test
  void shouldFilterByTenantScopedCustomerId() {
    seedCustomer(TENANT_ID, "customer-a");
    seedCustomer(TENANT_ID, "customer-b");
    seedInvoices(
        TENANT_ID,
        "customer-a",
        List.of(record("invoice-a", "A-1", "100.00", "0.00", "2026-07-01", "2026-08-10", false)));
    seedInvoices(
        TENANT_ID,
        "customer-b",
        List.of(record("invoice-b", "B-1", "100.00", "0.00", "2026-07-02", "2026-08-10", false)));
    BusinessPartnerId customerA = customerId(TENANT_ID, "customer-a");

    InvoiceQueryPage page =
        invoiceQueryRepository.findPage(
            new InvoiceQueryCriteria(
                OperationsTenantId.of(TENANT_ID),
                Optional.of(customerA),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                50,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.ASC));

    assertEquals(List.of("A-1"), numbers(page));
  }

  private InvoiceQueryPage dueState(InvoiceDueState dueState) {
    return invoiceQueryRepository.findPage(
        new InvoiceQueryCriteria(
            OperationsTenantId.of(TENANT_ID),
            Optional.empty(),
            Set.of(),
            Optional.of(dueState),
            BUSINESS_DATE,
            0,
            50,
            InvoiceSortField.ISSUE_DATE,
            SortDirection.ASC));
  }

  private static InvoiceQueryCriteria criteria(int pageNumber, int pageSize) {
    return new InvoiceQueryCriteria(
        OperationsTenantId.of(TENANT_ID),
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        BUSINESS_DATE,
        pageNumber,
        pageSize,
        InvoiceSortField.DUE_DATE,
        SortDirection.ASC);
  }

  private static List<String> numbers(InvoiceQueryPage page) {
    return page.invoices().stream().map(invoice -> invoice.invoiceNumber().value()).toList();
  }

  private void seedCustomer(UUID tenantId, String sourceIdentity) {
    importBusinessPartnersUseCase.importPage(
        new ImportBusinessPartnersCommand(
            tenantId,
            SOURCE_SYSTEM_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(
                new BusinessPartnerImportRecord(
                    SourceRecordIdentity.sourceRecordId(sourceIdentity),
                    new SourceRecordVersion("v1"),
                    Optional.of(SOURCE_TIME),
                    new BusinessPartnerProfile(
                        "C-" + sourceIdentity, "Customer " + sourceIdentity, Optional.empty())))));
  }

  private void seedInvoices(
      UUID tenantId, String customerSourceIdentity, List<InvoiceImportRecord> records) {
    importInvoicesUseCase.importPage(
        new ImportInvoicesCommand(
            tenantId,
            SOURCE_SYSTEM_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            records.stream()
                .map(
                    record ->
                        new InvoiceImportRecord(
                            record.sourceIdentity(),
                            record.sourceVersion(),
                            record.sourceModifiedAt(),
                            SourceRecordIdentity.sourceRecordId(customerSourceIdentity),
                            record.invoiceNumber(),
                            record.originalAmount(),
                            record.paidAmount(),
                            record.issueDate(),
                            record.dueDate(),
                            record.cancelled()))
                .toList()));
  }

  private BusinessPartnerId customerId(UUID tenantId, String sourceIdentity) {
    return businessPartnerMappingRepository
        .findBySourceIdentity(
            OperationsTenantId.of(tenantId),
            SourceSystemId.of(SOURCE_SYSTEM_ID),
            SourceRecordIdentity.sourceRecordId(sourceIdentity))
        .orElseThrow()
        .businessPartnerId();
  }

  private static InvoiceImportRecord record(
      String sourceIdentity,
      String invoiceNumber,
      String originalAmount,
      String paidAmount,
      String issueDate,
      String dueDate,
      boolean cancelled) {
    return new InvoiceImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion("v1"),
        Optional.of(SOURCE_TIME),
        SourceRecordIdentity.sourceRecordId("placeholder"),
        new InvoiceNumber(invoiceNumber),
        Money.of(originalAmount, EUR),
        Money.of(paidAmount, EUR),
        LocalDate.parse(issueDate),
        LocalDate.parse(dueDate),
        cancelled);
  }
}
