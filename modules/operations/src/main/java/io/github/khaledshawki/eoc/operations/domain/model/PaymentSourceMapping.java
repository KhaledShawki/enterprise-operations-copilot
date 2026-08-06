package io.github.khaledshawki.eoc.operations.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable source-system identity and replay evidence for one canonical payment. */
public final class PaymentSourceMapping {

  private final OperationsTenantId tenantId;
  private final SourceSystemId sourceSystemId;
  private final SourceRecordIdentity sourceIdentity;
  private final PaymentId paymentId;
  private final SourceRecordVersion sourceVersion;
  private final Optional<Instant> sourceModifiedAt;
  private final SourceRecordFingerprint payloadFingerprint;

  private PaymentSourceMapping(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      PaymentId paymentId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    this.tenantId =
        Objects.requireNonNull(tenantId, "Payment source mapping tenant id cannot be null");
    this.sourceSystemId =
        Objects.requireNonNull(sourceSystemId, "Payment source mapping system id cannot be null");
    this.sourceIdentity =
        Objects.requireNonNull(sourceIdentity, "Payment source mapping identity cannot be null");
    this.paymentId = Objects.requireNonNull(paymentId, "Mapped payment id cannot be null");
    this.sourceVersion =
        Objects.requireNonNull(sourceVersion, "Payment source mapping version cannot be null");
    this.sourceModifiedAt =
        Objects.requireNonNull(
            sourceModifiedAt, "Payment source mapping modification timestamp cannot be null");
    this.payloadFingerprint =
        Objects.requireNonNull(
            payloadFingerprint, "Payment source mapping payload fingerprint cannot be null");
  }

  public static PaymentSourceMapping create(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      PaymentId paymentId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    return new PaymentSourceMapping(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        paymentId,
        sourceVersion,
        sourceModifiedAt,
        payloadFingerprint);
  }

  public static PaymentSourceMapping reconstitute(
      OperationsTenantId tenantId,
      SourceSystemId sourceSystemId,
      SourceRecordIdentity sourceIdentity,
      PaymentId paymentId,
      SourceRecordVersion sourceVersion,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint payloadFingerprint) {
    return create(
        tenantId,
        sourceSystemId,
        sourceIdentity,
        paymentId,
        sourceVersion,
        sourceModifiedAt,
        payloadFingerprint);
  }

  public PaymentSourceRecordDecision evaluate(
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
        return PaymentSourceRecordDecision.duplicate(this);
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
      return PaymentSourceRecordDecision.stale(this);
    }
    if (timestampComparison == 0) {
      throw new ConflictingSourceRecordVersionException(
          sourceIdentity, "different versions have the same source modification timestamp");
    }

    return PaymentSourceRecordDecision.accepted(
        new PaymentSourceMapping(
            tenantId,
            sourceSystemId,
            sourceIdentity,
            paymentId,
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

  public PaymentId paymentId() {
    return paymentId;
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
