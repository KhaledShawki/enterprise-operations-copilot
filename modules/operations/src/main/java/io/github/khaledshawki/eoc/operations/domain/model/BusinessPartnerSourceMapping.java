package io.github.khaledshawki.eoc.operations.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class BusinessPartnerSourceMapping {

  private final OperationsTenantId tenantId;
  private final SourceSystemId sourceSystemId;
  private final SourceRecordIdentity sourceIdentity;
  private final BusinessPartnerId businessPartnerId;
  private SourceRecordVersion sourceVersion;
  private Optional<Instant> sourceModifiedAt;

  private BusinessPartnerSourceMapping(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      BusinessPartnerId businessPartnerId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt) {
    this.tenantId = Objects.requireNonNull(tenantId, "Source mapping tenant id cannot be null");
    this.sourceSystemId =
        Objects.requireNonNull(sourceSystemId, "Source mapping system id cannot be null");
    this.sourceIdentity =
        Objects.requireNonNull(sourceIdentity, "Source mapping identity cannot be null");
    this.businessPartnerId =
        Objects.requireNonNull(businessPartnerId, "Mapped business partner id cannot be null");
    this.sourceVersion =
        Objects.requireNonNull(sourceVersion, "Source mapping version cannot be null");
    this.sourceModifiedAt =
        Objects.requireNonNull(
            sourceModifiedAt, "Source mapping modification timestamp cannot be null");
  }

  public static BusinessPartnerSourceMapping create(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      BusinessPartnerId businessPartnerId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt) {
    return new BusinessPartnerSourceMapping(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        businessPartnerId,
        sourceVersion,
        sourceModifiedAt);
  }

  public static BusinessPartnerSourceMapping reconstitute(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      BusinessPartnerId businessPartnerId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt) {
    return create(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        businessPartnerId,
        sourceVersion,
        sourceModifiedAt);
  }

  /**
   * Accepts an unseen version only when it is not provably stale. Opaque versions are compared for
   * equality only; timestamps provide ordering evidence when the source supplies them.
   */
  public SourceRecordAcceptance accept(
      SourceRecordVersion incomingVersion, Optional<Instant> incomingModifiedAt) {
    Objects.requireNonNull(incomingVersion, "Incoming source version cannot be null");
    Objects.requireNonNull(
        incomingModifiedAt, "Incoming source modification timestamp cannot be null");

    if (sourceVersion.equals(incomingVersion)) {
      return SourceRecordAcceptance.DUPLICATE;
    }

    if (sourceModifiedAt.isPresent() && incomingModifiedAt.isEmpty()) {
      throw new ConflictingSourceRecordVersionException(
          sourceIdentity, "incoming version has no timestamp after ordered versions were observed");
    }

    if (sourceModifiedAt.isPresent() && incomingModifiedAt.isPresent()) {
      int comparison = incomingModifiedAt.orElseThrow().compareTo(sourceModifiedAt.orElseThrow());
      if (comparison < 0) {
        return SourceRecordAcceptance.STALE;
      }
      if (comparison == 0) {
        throw new ConflictingSourceRecordVersionException(
            sourceIdentity, "different versions have the same source modification timestamp");
      }
    }

    sourceVersion = incomingVersion;
    sourceModifiedAt = incomingModifiedAt;
    return SourceRecordAcceptance.ACCEPTED;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public SourceSystemId sourceSystemId() {
    return sourceSystemId;
  }

  public SourceRecordIdentity sourceIdentity() {
    return sourceIdentity;
  }

  public BusinessPartnerId businessPartnerId() {
    return businessPartnerId;
  }

  public SourceRecordVersion sourceVersion() {
    return sourceVersion;
  }

  public Optional<Instant> sourceModifiedAt() {
    return sourceModifiedAt;
  }
}
