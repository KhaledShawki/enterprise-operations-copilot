package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;

public final class ConnectorDeadLetterReplayLimitExceededException extends RuntimeException {

  public ConnectorDeadLetterReplayLimitExceededException(
      ConnectorDeadLetterReference reference, int maxReplayGeneration) {
    super(
        "Connector dead letter at partition "
            + reference.partition()
            + " offset "
            + reference.offset()
            + " has reached replay generation limit "
            + maxReplayGeneration);
  }
}
