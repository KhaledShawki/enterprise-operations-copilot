package io.github.khaledshawki.eoc.operations.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable source-system identity and replay evidence for one canonical invoice. */
public final class InvoiceSourceMapping {

  private final OperationsTenantId tenantId;
  private final SourceSystemId sourceSystemId;
  private final SourceRecordIdentity sourceIdentity;
  private final InvoiceId invoiceId;
  private final SourceRecordVersion sourceVersion;
  private final Optional<Instant> sourceModifiedAt;
  private final SourceRecordFingerprint payloadFingerprint;

  private InvoiceSourceMapping(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      InvoiceId invoiceId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    this.tenantId =
        Objects.requireNonNull(tenantId, "Invoice source mapping tenant id cannot be null");
    this.sourceSystemId =
        Objects.requireNonNull(sourceSystemId, "Invoice source mapping system id cannot be null");
    this.sourceIdentity =
        Objects.requireNonNull(sourceIdentity, "Invoice source mapping identity cannot be null");
    this.invoiceId = Objects.requireNonNull(invoiceId, "Mapped invoice id cannot be null");
    this.sourceVersion =
        Objects.requireNonNull(sourceVersion, "Invoice source mapping version cannot be null");
    this.sourceModifiedAt =
        Objects.requireNonNull(
            sourceModifiedAt, "Invoice source mapping modification timestamp cannot be null");
    this.payloadFingerprint =
        Objects.requireNonNull(
            payloadFingerprint, "Invoice source mapping payload fingerprint cannot be null");
  }

  public static InvoiceSourceMapping create(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      InvoiceId invoiceId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    return new InvoiceSourceMapping(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        invoiceId,
        sourceVersion,
        sourceModifiedAt,
        payloadFingerprint);
  }

  public static InvoiceSourceMapping reconstitute(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      InvoiceId invoiceId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    return create(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        invoiceId,
        sourceVersion,
        sourceModifiedAt,
        payloadFingerprint);
  }

  public InvoiceSourceRecordDecision evaluate(
      SourceRecordVersion incomingVersion,
      Optional<Instant> incomingModifiedAt,
      SourceRecordFingerprint incomingFingerprint) {
    Objects.requireNonNull(incomingVersion, "Incoming source version cannot be null");
    Objects.requireNonNull(
        incomingModifiedAt, "Incoming source modification timestamp cannot be null");
    Objects.requireNonNull(incomingFingerprint, "Incoming payload fingerprint cannot be null");

    if (sourceVersion.equals(incomingVersion)) {
      if (sourceModifiedAt.equals(incomingModifiedAt)
          && payloadFingerprint.equals(incomingFingerprint)) {
        return InvoiceSourceRecordDecision.duplicate(this);
      }
      throw new ConflictingSourceRecordReplayException(
          sourceIdentity,
          incomingVersion,
          "equal source version carries different modification evidence or payload");
    }

    if (sourceModifiedAt.isEmpty() || incomingModifiedAt.isEmpty()) {
      throw new ConflictingSourceRecordVersionException(
          sourceIdentity, "different versions do not have comparable modification timestamps");
    }

    int timestampComparison =
        incomingModifiedAt.orElseThrow().compareTo(sourceModifiedAt.orElseThrow());
    if (timestampComparison < 0) {
      return InvoiceSourceRecordDecision.stale(this);
    }
    if (timestampComparison == 0) {
      throw new ConflictingSourceRecordVersionException(
          sourceIdentity, "different versions have the same source modification timestamp");
    }

    return InvoiceSourceRecordDecision.accepted(
        new InvoiceSourceMapping(
            tenantId,
            sourceSystemId,
            sourceIdentity,
            invoiceId,
            incomingVersion,
            incomingModifiedAt,
            incomingFingerprint));
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

  public InvoiceId invoiceId() {
    return invoiceId;
  }

  public SourceRecordVersion sourceVersion() {
    return sourceVersion;
  }

  public Optional<Instant> sourceModifiedAt() {
    return sourceModifiedAt;
  }

  public SourceRecordFingerprint payloadFingerprint() {
    return payloadFingerprint;
  }
}
