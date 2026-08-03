package io.github.khaledshawki.eoc.connectormanagement.application.model.outbox;

import java.time.Duration;

public record PublishConnectorOutboxBatchCommand(
    String workerId, int batchSize, Duration claimLease) {

  public PublishConnectorOutboxBatchCommand {
    ConnectorOutboxClaim.requireClaimOwner(workerId);
    if (batchSize < 1 || batchSize > ConnectorOutboxClaim.MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Outbox batch size must be between 1 and 100");
    }
    if (claimLease == null) {
      throw new NullPointerException("Claim lease cannot be null");
    }
    if (claimLease.isZero()
        || claimLease.isNegative()
        || claimLease.compareTo(ConnectorOutboxClaim.MAX_CLAIM_LEASE) > 0) {
      throw new IllegalArgumentException(
          "Outbox claim lease must be positive and at most one hour");
    }
  }
}
