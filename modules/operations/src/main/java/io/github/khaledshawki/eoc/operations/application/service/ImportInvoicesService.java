package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerRoleRequiredException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceCustomerSourceMappingNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.event.OperationsIntegrationEventFactory;
import io.github.khaledshawki.eoc.operations.application.model.event.SourceRecordEvidence;
import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportFingerprint;
import io.github.khaledshawki.eoc.operations.application.model.importing.InvoiceImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceImportResult;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventOutbox;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.Invoice;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceRecordDecision;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordAcceptance;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ImportInvoicesService implements ImportInvoicesUseCase {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceSourceMappingRepository sourceMappingRepository;
  private final InvoiceImportReceiptRepository importReceiptRepository;
  private final BusinessPartnerRepository businessPartnerRepository;
  private final BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository;
  private final InvoiceImportUnitOfWork unitOfWork;
  private final OperationsIntegrationEventOutbox eventOutbox;
  private final Clock clock;

  public ImportInvoicesService(
      InvoiceRepository invoiceRepository,
      InvoiceSourceMappingRepository sourceMappingRepository,
      InvoiceImportReceiptRepository importReceiptRepository,
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository,
      InvoiceImportUnitOfWork unitOfWork,
      OperationsIntegrationEventOutbox eventOutbox,
      Clock clock) {
    this.invoiceRepository =
        Objects.requireNonNull(invoiceRepository, "Invoice repository cannot be null");
    this.sourceMappingRepository =
        Objects.requireNonNull(
            sourceMappingRepository, "Invoice source mapping repository cannot be null");
    this.importReceiptRepository =
        Objects.requireNonNull(
            importReceiptRepository, "Invoice import receipt repository cannot be null");
    this.businessPartnerRepository =
        Objects.requireNonNull(
            businessPartnerRepository, "Business partner repository cannot be null");
    this.businessPartnerSourceMappingRepository =
        Objects.requireNonNull(
            businessPartnerSourceMappingRepository,
            "Business partner source mapping repository cannot be null");
    this.unitOfWork =
        Objects.requireNonNull(unitOfWork, "Invoice import unit of work cannot be null");
    this.eventOutbox =
        Objects.requireNonNull(eventOutbox, "Operations event outbox cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public InvoiceImportResult importPage(ImportInvoicesCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    return unitOfWork.execute(() -> importPageWithinUnitOfWork(command));
  }

  private InvoiceImportResult importPageWithinUnitOfWork(ImportInvoicesCommand command) {
    OperationsTenantId tenantId = OperationsTenantId.of(command.tenantId());
    SourceSystemId sourceSystemId = SourceSystemId.of(command.sourceSystemId());
    String payloadFingerprint = InvoiceImportFingerprint.page(command);

    Optional<InvoiceImportReceipt> existingReceipt =
        importReceiptRepository.find(
            tenantId, sourceSystemId, command.importBatchId(), command.pageAcceptanceId());
    if (existingReceipt.isPresent()) {
      InvoiceImportReceipt receipt = existingReceipt.orElseThrow();
      if (!receipt.payloadFingerprint().equals(payloadFingerprint)) {
        throw new ImportPageAcceptanceConflictException(command.pageAcceptanceId());
      }
      return receipt.result();
    }

    ImportPlan plan = preflight(tenantId, sourceSystemId, command);
    Instant acceptedAt = clock.instant();
    plan.persist(invoiceRepository, sourceMappingRepository, eventOutbox, acceptedAt);

    InvoiceImportResult result = plan.counters().toResult(command.pageAcceptanceId(), acceptedAt);
    return importReceiptRepository
        .save(
            tenantId,
            sourceSystemId,
            command.importBatchId(),
            new InvoiceImportReceipt(payloadFingerprint, result))
        .result();
  }

  private ImportPlan preflight(
      OperationsTenantId tenantId, SourceSystemId sourceSystemId, ImportInvoicesCommand command) {
    LinkedHashMap<SourceRecordIdentity, PlannedInvoiceState> states = new LinkedHashMap<>();
    Map<SourceRecordIdentity, BusinessPartner> customers = new LinkedHashMap<>();
    ImportCounters counters = new ImportCounters();

    for (InvoiceImportRecord record : command.records()) {
      PlannedInvoiceState state =
          states.computeIfAbsent(
              record.sourceIdentity(),
              ignored -> loadState(tenantId, sourceSystemId, record.sourceIdentity()));
      SourceRecordFingerprint fingerprint = InvoiceImportFingerprint.record(record);
      preflightRecord(tenantId, sourceSystemId, record, fingerprint, state, customers, counters);
    }

    return new ImportPlan(states, counters);
  }

  private PlannedInvoiceState loadState(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity) {
    return sourceMappingRepository
        .findBySourceIdentity(tenantId, sourceSystemId, sourceIdentity)
        .map(PlannedInvoiceState::existing)
        .orElseGet(PlannedInvoiceState::unmapped);
  }

  private void preflightRecord(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      InvoiceImportRecord record,
      SourceRecordFingerprint fingerprint,
      PlannedInvoiceState state,
      Map<SourceRecordIdentity, BusinessPartner> customers,
      ImportCounters counters) {
    if (state.mapping().isEmpty()) {
      BusinessPartner customer =
          resolveCustomer(tenantId, sourceSystemId, record.customerSourceIdentity(), customers);
      Invoice invoice =
          Invoice.importCustomerInvoice(
              tenantId,
              customer.id(),
              record.invoiceNumber(),
              record.originalAmount(),
              record.paidAmount(),
              record.issueDate(),
              record.dueDate(),
              record.cancelled());
      InvoiceSourceMapping mapping =
          InvoiceSourceMapping.create(
              tenantId,
              sourceSystemId,
              record.sourceIdentity(),
              invoice.id(),
              record.sourceVersion(),
              record.sourceModifiedAt(),
              fingerprint);
      state.replace(invoice, mapping);
      counters.created++;
      return;
    }

    InvoiceSourceMapping currentMapping = state.mapping().orElseThrow();
    InvoiceSourceRecordDecision decision =
        currentMapping.evaluate(record.sourceVersion(), record.sourceModifiedAt(), fingerprint);
    SourceRecordAcceptance acceptance = decision.acceptance();
    switch (acceptance) {
      case DUPLICATE -> counters.duplicate++;
      case STALE -> counters.stale++;
      case ACCEPTED -> {
        Invoice currentInvoice =
            state.invoice().orElseGet(() -> loadInvoice(tenantId, currentMapping));
        BusinessPartner customer =
            resolveCustomer(tenantId, sourceSystemId, record.customerSourceIdentity(), customers);
        Invoice synchronizedInvoice =
            Invoice.reconstitute(
                currentInvoice.id(),
                tenantId,
                customer.id(),
                record.invoiceNumber(),
                record.originalAmount(),
                record.paidAmount(),
                record.issueDate(),
                record.dueDate(),
                record.cancelled());
        state.replace(synchronizedInvoice, decision.resultingMapping());
        counters.updated++;
      }
    }
  }

  private Invoice loadInvoice(OperationsTenantId tenantId, InvoiceSourceMapping sourceMapping) {
    return invoiceRepository
        .findById(tenantId, sourceMapping.invoiceId())
        .orElseThrow(() -> new InvoiceSourceMappingCorruptedException(sourceMapping));
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
                    new InvoiceCustomerSourceMappingNotFoundException(
                        tenantId, sourceSystemId, customerSourceIdentity));
    BusinessPartner customer =
        businessPartnerRepository
            .findById(tenantId, customerMapping.businessPartnerId())
            .orElseThrow(() -> new BusinessPartnerSourceMappingCorruptedException(customerMapping));
    if (!customer.roles().contains(BusinessPartnerRole.CUSTOMER)) {
      throw new InvoiceCustomerRoleRequiredException(customer.id());
    }

    customers.put(customerSourceIdentity, customer);
    return customer;
  }

  private static final class PlannedInvoiceState {

    private Invoice invoice;
    private InvoiceSourceMapping mapping;
    private boolean dirty;

    private PlannedInvoiceState(Invoice invoice, InvoiceSourceMapping mapping, boolean dirty) {
      this.invoice = invoice;
      this.mapping = mapping;
      this.dirty = dirty;
    }

    static PlannedInvoiceState unmapped() {
      return new PlannedInvoiceState(null, null, false);
    }

    static PlannedInvoiceState existing(InvoiceSourceMapping mapping) {
      return new PlannedInvoiceState(
          null, Objects.requireNonNull(mapping, "Invoice source mapping cannot be null"), false);
    }

    Optional<Invoice> invoice() {
      return Optional.ofNullable(invoice);
    }

    Optional<InvoiceSourceMapping> mapping() {
      return Optional.ofNullable(mapping);
    }

    void replace(Invoice invoice, InvoiceSourceMapping mapping) {
      this.invoice = Objects.requireNonNull(invoice, "Planned invoice cannot be null");
      this.mapping =
          Objects.requireNonNull(mapping, "Planned invoice source mapping cannot be null");
      dirty = true;
    }

    void persist(
        InvoiceRepository invoiceRepository,
        InvoiceSourceMappingRepository sourceMappingRepository,
        OperationsIntegrationEventOutbox eventOutbox,
        Instant acceptedAt) {
      if (!dirty) {
        return;
      }
      Invoice persistedInvoice = invoiceRepository.save(Objects.requireNonNull(invoice));
      InvoiceSourceMapping persistedMapping =
          sourceMappingRepository.save(Objects.requireNonNull(mapping));
      eventOutbox.append(
          OperationsIntegrationEventFactory.pendingInvoiceSynchronized(
              persistedInvoice,
              SourceRecordEvidence.from(
                  persistedMapping.sourceSystemId(),
                  persistedMapping.sourceIdentity(),
                  persistedMapping.sourceVersion(),
                  persistedMapping.sourceModifiedAt()),
              acceptedAt));
    }
  }

  private record ImportPlan(
      LinkedHashMap<SourceRecordIdentity, PlannedInvoiceState> states, ImportCounters counters) {

    private ImportPlan {
      Objects.requireNonNull(states, "Planned invoice states cannot be null");
      Objects.requireNonNull(counters, "Invoice import counters cannot be null");
    }

    void persist(
        InvoiceRepository invoiceRepository,
        InvoiceSourceMappingRepository sourceMappingRepository,
        OperationsIntegrationEventOutbox eventOutbox,
        Instant acceptedAt) {
      states
          .values()
          .forEach(
              state ->
                  state.persist(
                      invoiceRepository, sourceMappingRepository, eventOutbox, acceptedAt));
    }
  }

  private static final class ImportCounters {

    private long created;
    private long updated;
    private long duplicate;
    private long stale;

    InvoiceImportResult toResult(java.util.UUID pageAcceptanceId, java.time.Instant acceptedAt) {
      long fetched =
          Math.addExact(Math.addExact(created, updated), Math.addExact(duplicate, stale));
      return new InvoiceImportResult(
          pageAcceptanceId, fetched, created, updated, duplicate, stale, acceptedAt);
    }
  }
}
