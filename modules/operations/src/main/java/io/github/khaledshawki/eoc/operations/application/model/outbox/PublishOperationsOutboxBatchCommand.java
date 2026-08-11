package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.time.Duration;

public record PublishOperationsOutboxBatchCommand(
    String workerId, int batchSize, Duration claimLease) {

  public PublishOperationsOutboxBatchCommand {
    OperationsOutboxClaim.requireClaimOwner(workerId);
    if (batchSize < 1 || batchSize > OperationsOutboxClaim.MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Operations outbox batch size must be between 1 and 100");
    }
    if (claimLease == null) {
      throw new NullPointerException("Claim lease cannot be null");
    }
    if (claimLease.isZero()
        || claimLease.isNegative()
        || claimLease.compareTo(OperationsOutboxClaim.MAX_CLAIM_LEASE) > 0) {
      throw new IllegalArgumentException(
          "Operations outbox claim lease must be positive and at most one hour");
    }
  }
}
