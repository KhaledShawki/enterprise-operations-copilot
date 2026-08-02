package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.BusinessPartnerSourceMappingCorruptedException;
import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.model.importing.BusinessPartnerImportReceipt;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordAcceptance;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

public final class ImportBusinessPartnersService implements ImportBusinessPartnersUseCase {

  private final BusinessPartnerRepository businessPartnerRepository;
  private final BusinessPartnerSourceMappingRepository sourceMappingRepository;
  private final BusinessPartnerImportReceiptRepository importReceiptRepository;
  private final Clock clock;

  public ImportBusinessPartnersService(
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository sourceMappingRepository,
      BusinessPartnerImportReceiptRepository importReceiptRepository,
      Clock clock) {
    this.businessPartnerRepository =
        Objects.requireNonNull(
            businessPartnerRepository, "Business partner repository cannot be null");
    this.sourceMappingRepository =
        Objects.requireNonNull(sourceMappingRepository, "Source mapping repository cannot be null");
    this.importReceiptRepository =
        Objects.requireNonNull(importReceiptRepository, "Import receipt repository cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public BusinessPartnerImportResult importPage(ImportBusinessPartnersCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");
    OperationsTenantId tenantId = OperationsTenantId.of(command.tenantId());
    SourceSystemId sourceSystemId = SourceSystemId.of(command.sourceSystemId());
    String payloadFingerprint = fingerprint(command);

    Optional<BusinessPartnerImportReceipt> existingReceipt =
        importReceiptRepository.find(
            tenantId, sourceSystemId, command.importBatchId(), command.pageAcceptanceId());
    if (existingReceipt.isPresent()) {
      BusinessPartnerImportReceipt receipt = existingReceipt.orElseThrow();
      if (!receipt.payloadFingerprint().equals(payloadFingerprint)) {
        throw new ImportPageAcceptanceConflictException(command.pageAcceptanceId());
      }
      return receipt.result();
    }

    ImportCounters counters = new ImportCounters();
    for (BusinessPartnerImportRecord record : command.records()) {
      importRecord(tenantId, sourceSystemId, record, counters);
    }

    BusinessPartnerImportResult result =
        counters.toResult(command.pageAcceptanceId(), clock.instant());
    return importReceiptRepository
        .save(
            tenantId,
            sourceSystemId,
            command.importBatchId(),
            new BusinessPartnerImportReceipt(payloadFingerprint, result))
        .result();
  }

  private static String fingerprint(ImportBusinessPartnersCommand command) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(command.records().size()).array());
      for (BusinessPartnerImportRecord record : command.records()) {
        updateText(digest, record.sourceIdentity().kind().name());
        updateText(digest, record.sourceIdentity().value());
        updateText(digest, record.sourceVersion().value());
        updateOptionalText(digest, record.sourceModifiedAt().map(java.time.Instant::toString));
        updateText(digest, record.profile().partnerNumber());
        updateText(digest, record.profile().displayName());
        updateOptionalText(digest, record.profile().emailAddress());
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 message digest is not available", exception);
    }
  }

  private static void updateOptionalText(MessageDigest digest, Optional<String> value) {
    digest.update((byte) (value.isPresent() ? 1 : 0));
    value.ifPresent(text -> updateText(digest, text));
  }

  private static void updateText(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private void importRecord(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      BusinessPartnerImportRecord record,
      ImportCounters counters) {
    Optional<BusinessPartnerSourceMapping> existingMapping =
        sourceMappingRepository.findBySourceIdentity(
            tenantId, sourceSystemId, record.sourceIdentity());
    if (existingMapping.isEmpty()) {
      BusinessPartner businessPartner =
          businessPartnerRepository.save(
              BusinessPartner.importCustomer(tenantId, record.profile()));
      sourceMappingRepository.save(
          BusinessPartnerSourceMapping.create(
              tenantId,
              sourceSystemId,
              record.sourceIdentity(),
              businessPartner.id(),
              record.sourceVersion(),
              record.sourceModifiedAt()));
      counters.created++;
      return;
    }

    BusinessPartnerSourceMapping sourceMapping = existingMapping.orElseThrow();
    SourceRecordAcceptance acceptance =
        sourceMapping.accept(record.sourceVersion(), record.sourceModifiedAt());
    switch (acceptance) {
      case DUPLICATE -> counters.duplicate++;
      case STALE -> counters.stale++;
      case ACCEPTED -> {
        BusinessPartner businessPartner =
            businessPartnerRepository
                .findById(tenantId, sourceMapping.businessPartnerId())
                .orElseThrow(
                    () -> new BusinessPartnerSourceMappingCorruptedException(sourceMapping));
        businessPartner.synchronizeCustomer(record.profile());
        businessPartnerRepository.save(businessPartner);
        sourceMappingRepository.save(sourceMapping);
        counters.updated++;
      }
    }
  }

  private static final class ImportCounters {

    private long created;
    private long updated;
    private long duplicate;
    private long stale;

    BusinessPartnerImportResult toResult(
        java.util.UUID pageAcceptanceId, java.time.Instant acceptedAt) {
      long fetched =
          Math.addExact(Math.addExact(created, updated), Math.addExact(duplicate, stale));
      return new BusinessPartnerImportResult(
          pageAcceptanceId, fetched, created, updated, duplicate, stale, acceptedAt);
    }
  }
}
