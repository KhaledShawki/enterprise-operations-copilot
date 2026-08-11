package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Duration;

public record PublishConnectorDeadLetterReplayBatchCommand(
    String workerId, int batchSize, Duration claimLease) {

  public PublishConnectorDeadLetterReplayBatchCommand {
    new ConnectorDeadLetterReplayClaim(workerId, batchSize, java.time.Instant.EPOCH, claimLease);
  }
}
