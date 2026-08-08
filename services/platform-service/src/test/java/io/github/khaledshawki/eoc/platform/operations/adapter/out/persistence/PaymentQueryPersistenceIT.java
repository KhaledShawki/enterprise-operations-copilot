package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryCriteria;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentQueryPage;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentQueryRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
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
class PaymentQueryPersistenceIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-08T08:00:00Z");
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Autowired private ImportBusinessPartnersUseCase importBusinessPartnersUseCase;
  @Autowired private ImportPaymentsUseCase importPaymentsUseCase;
  @Autowired private BusinessPartnerSourceMappingRepository businessPartnerMappingRepository;
  @Autowired private PaymentQueryRepository paymentQueryRepository;
  @Autowired private SpringDataPaymentRepository springDataPaymentRepository;
  @Autowired private SpringDataPaymentSourceMappingRepository springDataPaymentMappingRepository;
  @Autowired private SpringDataPaymentImportReceiptRepository springDataPaymentReceiptRepository;
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
    springDataPaymentReceiptRepository.deleteAllInBatch();
    springDataPaymentMappingRepository.deleteAllInBatch();
    springDataPaymentRepository.deleteAllInBatch();
    springDataInvoiceReceiptRepository.deleteAllInBatch();
    springDataInvoiceMappingRepository.deleteAllInBatch();
    springDataInvoiceRepository.deleteAllInBatch();
    springDataBusinessPartnerReceiptRepository.deleteAllInBatch();
    springDataBusinessPartnerMappingRepository.deleteAllInBatch();
    springDataBusinessPartnerRepository.deleteAllInBatch();
  }

  @Test
  void shouldApplyTenantIsolationAndDeterministicPaymentDatePagination() {
    seedCustomer(TENANT_ID, "customer-a");
    seedPayments(
        TENANT_ID,
        "customer-a",
        List.of(
            record("payment-3", "30.00", "2026-08-03", false),
            record("payment-1", "10.00", "2026-08-01", false),
            record("payment-2", "20.00", "2026-08-02", false)));
    seedCustomer(OTHER_TENANT_ID, "customer-a");
    seedPayments(
        OTHER_TENANT_ID,
        "customer-a",
        List.of(record("other-payment", "999.00", "2026-08-01", false)));

    PaymentQueryPage first = paymentQueryRepository.findPage(criteria(0, 2));
    PaymentQueryPage second = paymentQueryRepository.findPage(criteria(1, 2));

    assertEquals(3, first.totalElements());
    assertEquals(List.of("10.00", "20.00"), amounts(first));
    assertEquals(List.of("30.00"), amounts(second));
    assertTrue(first.hasNext());
  }

  @Test
  void shouldFilterByCanonicalPaymentStatus() {
    seedCustomer(TENANT_ID, "customer-a");
    seedPayments(
        TENANT_ID,
        "customer-a",
        List.of(
            record("recorded", "10.00", "2026-08-01", false),
            record("reversed", "20.00", "2026-08-02", true)));

    PaymentQueryPage recorded =
        paymentQueryRepository.findPage(
            queryCriteria(
                Optional.empty(),
                Set.of(PaymentStatus.RECORDED),
                Optional.empty(),
                Optional.empty()));
    PaymentQueryPage reversed =
        paymentQueryRepository.findPage(
            queryCriteria(
                Optional.empty(),
                Set.of(PaymentStatus.REVERSED),
                Optional.empty(),
                Optional.empty()));

    assertEquals(List.of("10.00"), amounts(recorded));
    assertEquals(List.of("20.00"), amounts(reversed));
    assertTrue(reversed.payments().getFirst().reversed());
  }

  @Test
  void shouldApplyInclusivePaymentDateRange() {
    seedCustomer(TENANT_ID, "customer-a");
    seedPayments(
        TENANT_ID,
        "customer-a",
        List.of(
            record("early", "10.00", "2026-08-01", false),
            record("from", "20.00", "2026-08-05", false),
            record("to", "30.00", "2026-08-10", false),
            record("late", "40.00", "2026-08-11", false)));

    PaymentQueryPage page =
        paymentQueryRepository.findPage(
            queryCriteria(
                Optional.empty(),
                Set.of(),
                Optional.of(LocalDate.of(2026, 8, 5)),
                Optional.of(LocalDate.of(2026, 8, 10))));

    assertEquals(List.of("20.00", "30.00"), amounts(page));
  }

  @Test
  void shouldFilterByTenantScopedCustomerId() {
    seedCustomer(TENANT_ID, "customer-a");
    seedCustomer(TENANT_ID, "customer-b");
    seedPayments(
        TENANT_ID, "customer-a", List.of(record("payment-a", "10.00", "2026-08-01", false)));
    seedPayments(
        TENANT_ID, "customer-b", List.of(record("payment-b", "20.00", "2026-08-02", false)));
    BusinessPartnerId customerA = customerId(TENANT_ID, "customer-a");

    PaymentQueryPage page =
        paymentQueryRepository.findPage(
            queryCriteria(Optional.of(customerA), Set.of(), Optional.empty(), Optional.empty()));

    assertEquals(List.of("10.00"), amounts(page));
    assertEquals(customerA, page.payments().getFirst().customerId());
  }

  private static PaymentQueryCriteria criteria(int pageNumber, int pageSize) {
    return new PaymentQueryCriteria(
        OperationsTenantId.of(TENANT_ID),
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        Optional.empty(),
        pageNumber,
        pageSize,
        PaymentSortField.PAYMENT_DATE,
        SortDirection.ASC);
  }

  private static PaymentQueryCriteria queryCriteria(
      Optional<BusinessPartnerId> customerId,
      Set<PaymentStatus> statuses,
      Optional<LocalDate> from,
      Optional<LocalDate> to) {
    return new PaymentQueryCriteria(
        OperationsTenantId.of(TENANT_ID),
        customerId,
        statuses,
        from,
        to,
        0,
        50,
        PaymentSortField.PAYMENT_DATE,
        SortDirection.ASC);
  }

  private static List<String> amounts(PaymentQueryPage page) {
    return page.payments().stream()
        .map(payment -> payment.amount().amount().toPlainString())
        .toList();
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

  private void seedPayments(
      UUID tenantId, String customerSourceIdentity, List<PaymentImportRecord> records) {
    importPaymentsUseCase.importPage(
        new ImportPaymentsCommand(
            tenantId,
            SOURCE_SYSTEM_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            records.stream()
                .map(
                    record ->
                        new PaymentImportRecord(
                            record.sourceIdentity(),
                            record.sourceVersion(),
                            record.sourceModifiedAt(),
                            SourceRecordIdentity.sourceRecordId(customerSourceIdentity),
                            record.amount(),
                            record.paymentDate(),
                            record.reversed()))
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

  private static PaymentImportRecord record(
      String sourceIdentity, String amount, String paymentDate, boolean reversed) {
    return new PaymentImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceIdentity),
        new SourceRecordVersion("v1"),
        Optional.of(SOURCE_TIME),
        SourceRecordIdentity.sourceRecordId("placeholder"),
        Money.of(amount, EUR),
        LocalDate.parse(paymentDate),
        reversed);
  }
}
