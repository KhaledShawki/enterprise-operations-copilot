package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportFingerprint;
import io.github.khaledshawki.eoc.operations.application.model.importing.PaymentImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.Payment;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentSourceRecordDecision;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordAcceptance;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ImportPaymentsService implements ImportPaymentsUseCase {

  private final PaymentRepository paymentRepository;
  private final PaymentSourceMappingRepository sourceMappingRepository;
  private final PaymentImportReceiptRepository importReceiptRepository;
  private final BusinessPartnerRepository businessPartnerRepository;
  private final BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository;
  private final PaymentImportUnitOfWork unitOfWork;
  private final Clock clock;

  public ImportPaymentsService(
      PaymentRepository paymentRepository,
      PaymentSourceMappingRepository sourceMappingRepository,
      PaymentImportReceiptRepository importReceiptRepository,
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository,
      PaymentImportUnitOfWork unitOfWork,
      Clock clock) {
    this.paymentRepository =
        Objects.requireNonNull(paymentRepository, "Payment repository cannot be null");
    this.sourceMappingRepository =
        Objects.requireNonNull(
            sourceMappingRepository, "Payment source mapping repository cannot be null");
    this.importReceiptRepository =
        Objects.requireNonNull(
            importReceiptRepository, "Payment import receipt repository cannot be null");
    this.businessPartnerRepository =
        Objects.requireNonNull(
            businessPartnerRepository, "Business partner repository cannot be null");
    this.businessPartnerSourceMappingRepository =
        Objects.requireNonNull(
            businessPartnerSourceMappingRepository,
            "Business partner source mapping repository cannot be null");
    this.unitOfWork =
        Objects.requireNonNull(unitOfWork, "Payment import unit of work cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public PaymentImportResult importPage(ImportPaymentsCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    return unitOfWork.execute(() -> importPageWithinUnitOfWork(command));
  }

  private PaymentImportResult importPageWithinUnitOfWork(ImportPaymentsCommand command) {
    OperationsTenantId tenantId = OperationsTenantId.of(command.tenantId());
    SourceSystemId sourceSystemId = SourceSystemId.of(command.sourceSystemId());
    String payloadFingerprint = PaymentImportFingerprint.page(command);

    Optional<PaymentImportReceipt> existingReceipt =
        importReceiptRepository.find(
            tenantId, sourceSystemId, command.importBatchId(), command.pageAcceptanceId());
    if (existingReceipt.isPresent()) {
      PaymentImportReceipt receipt = existingReceipt.orElseThrow();
      if (!receipt.payloadFingerprint().equals(payloadFingerprint)) {
        throw new ImportPageAcceptanceConflictException(command.pageAcceptanceId());
      }
      return receipt.result();
    }

    ImportPlan plan = preflight(tenantId, sourceSystemId, command);
    plan.persist(paymentRepository, sourceMappingRepository);

    PaymentImportResult result =
        plan.counters().toResult(command.pageAcceptanceId(), clock.instant());
    return importReceiptRepository
        .save(
            tenantId,
            sourceSystemId,
            command.importBatchId(),
            new PaymentImportReceipt(payloadFingerprint, result))
        .result();
  }

  private ImportPlan preflight(
      OperationsTenantId tenantId, SourceSystemId sourceSystemId, ImportPaymentsCommand command) {
    LinkedHashMap<SourceRecordIdentity, PlannedPaymentState> states = new LinkedHashMap<>();
    Map<SourceRecordIdentity, BusinessPartner> customers = new LinkedHashMap<>();
    ImportCounters counters = new ImportCounters();

    for (PaymentImportRecord record : command.records()) {
      PlannedPaymentState state =
          states.computeIfAbsent(
              record.sourceIdentity(),
              ignored -> loadState(tenantId, sourceSystemId, record.sourceIdentity()));
      SourceRecordFingerprint fingerprint = PaymentImportFingerprint.record(record);
      preflightRecord(tenantId, sourceSystemId, record, fingerprint, state, customers, counters);
    }

    return new ImportPlan(states, counters);
  }

  private PlannedPaymentState loadState(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {
    return sourceMappingRepository
        .findBySourceIdentity(tenantId, sourceSystemId, sourceIdentity)
        .map(PlannedPaymentState::existing)
        .orElseGet(PlannedPaymentState::unmapped);
  }

  private void preflightRecord(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      PaymentImportRecord record,
      SourceRecordFingerprint fingerprint,
      PlannedPaymentState state,
      Map<SourceRecordIdentity, BusinessPartner> customers,
      ImportCounters counters) {
    if (state.mapping().isEmpty()) {
      BusinessPartner customer =
          resolveCustomer(tenantId, sourceSystemId, record.customerSourceIdentity(), customers);
      Payment payment =
          Payment.importCustomerPayment(
              tenantId, customer.id(), record.amount(), record.paymentDate(), record.reversed());
      PaymentSourceMapping mapping =
          PaymentSourceMapping.create(
              tenantId,
              sourceSystemId,
              record.sourceIdentity(),
              payment.id(),
              record.sourceVersion(),
              record.sourceModifiedAt(),
              fingerprint);
      state.replace(payment, mapping);
      counters.created++;
      return;
    }

    PaymentSourceMapping currentMapping = state.mapping().orElseThrow();
    PaymentSourceRecordDecision decision =
        currentMapping.evaluate(record.sourceVersion(), record.sourceModifiedAt(), fingerprint);
    SourceRecordAcceptance acceptance = decision.acceptance();
    switch (acceptance) {
      case DUPLICATE -> counters.duplicate++;
      case STALE -> counters.stale++;
      case ACCEPTED -> {
        Payment currentPayment =
            state.payment().orElseGet(() -> loadPayment(tenantId, currentMapping));
        BusinessPartner customer =
            resolveCustomer(tenantId, sourceSystemId, record.customerSourceIdentity(), customers);
        Payment synchronizedPayment =
            Payment.reconstitute(
                currentPayment.id(),
                tenantId,
                customer.id(),
                record.amount(),
                record.paymentDate(),
                record.reversed());
        state.replace(synchronizedPayment, decision.resultingMapping());
        counters.updated++;
      }
    }
  }

  private Payment loadPayment(OperationsTenantId tenantId, PaymentSourceMapping sourceMapping) {
    return paymentRepository
        .findById(tenantId, sourceMapping.paymentId())
        .orElseThrow(() -> new PaymentSourceMappingCorruptedException(sourceMapping));
  }

  private BusinessPartner resolveCustomer(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity customerSourceIdentity,
      Map<SourceRecordIdentity, BusinessPartner> customers) {
    BusinessPartner cached = customers.get(customerSourceIdentity);
    if (cached != null) {
      return cached;
    }

    BusinessPartnerSourceMapping customerMapping =
        businessPartnerSourceMappingRepository
            .findBySourceIdentity(tenantId, sourceSystemId, customerSourceIdentity)
            .orElseThrow(
                () ->
                    new PaymentCustomerSourceMappingNotFoundException(
                        tenantId, sourceSystemId, customerSourceIdentity));
    BusinessPartner customer =
        businessPartnerRepository
            .findById(tenantId, customerMapping.businessPartnerId())
            .orElseThrow(() -> new BusinessPartnerSourceMappingCorruptedException(customerMapping));
    if (!customer.roles().contains(BusinessPartnerRole.CUSTOMER)) {
      throw new PaymentCustomerRoleRequiredException(customer.id());
    }

    customers.put(customerSourceIdentity, customer);
    return customer;
  }

  private static final class PlannedPaymentState {

    private Payment payment;
    private PaymentSourceMapping mapping;
    private boolean dirty;

    private PlannedPaymentState(Payment payment, PaymentSourceMapping mapping, boolean dirty) {
      this.payment = payment;
      this.mapping = mapping;
      this.dirty = dirty;
    }

    static PlannedPaymentState unmapped() {
      return new PlannedPaymentState(null, null, false);
    }

    static PlannedPaymentState existing(PaymentSourceMapping mapping) {
      return new PlannedPaymentState(
          null, Objects.requireNonNull(mapping, "Payment source mapping cannot be null"), false);
    }

    Optional<Payment> payment() {
      return Optional.ofNullable(payment);
    }

    Optional<PaymentSourceMapping> mapping() {
      return Optional.ofNullable(mapping);
    }

    void replace(Payment payment, PaymentSourceMapping mapping) {
      this.payment = Objects.requireNonNull(payment, "Planned payment cannot be null");
      this.mapping =
          Objects.requireNonNull(mapping, "Planned payment source mapping cannot be null");
      dirty = true;
    }

    void persist(
        PaymentRepository paymentRepository,
        PaymentSourceMappingRepository sourceMappingRepository) {
      if (!dirty) {
        return;
      }
      paymentRepository.save(Objects.requireNonNull(payment));
      sourceMappingRepository.save(Objects.requireNonNull(mapping));
    }
  }

  private record ImportPlan(
      LinkedHashMap<SourceRecordIdentity, PlannedPaymentState> states, ImportCounters counters) {

    private ImportPlan {
      Objects.requireNonNull(states, "Planned payment states cannot be null");
      Objects.requireNonNull(counters, "Payment import counters cannot be null");
    }

    void persist(
        PaymentRepository paymentRepository,
        PaymentSourceMappingRepository sourceMappingRepository) {
      states.values().forEach(state -> state.persist(paymentRepository, sourceMappingRepository));
    }
  }

  private static final class ImportCounters {

    private long created;
    private long updated;
    private long duplicate;
    private long stale;

    PaymentImportResult toResult(java.util.UUID pageAcceptanceId, java.time.Instant acceptedAt) {
      long fetched =
          Math.addExact(Math.addExact(created, updated), Math.addExact(duplicate, stale));
      return new PaymentImportResult(
          pageAcceptanceId, fetched, created, updated, duplicate, stale, acceptedAt);
    }
  }
}
