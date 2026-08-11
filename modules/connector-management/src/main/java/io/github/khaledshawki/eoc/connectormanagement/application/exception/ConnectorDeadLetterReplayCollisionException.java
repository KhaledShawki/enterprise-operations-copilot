package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;

public final class ConnectorDeadLetterReplayCollisionException extends RuntimeException {

  public ConnectorDeadLetterReplayCollisionException(ConnectorDeadLetterReference reference) {
    super(
        "Connector dead-letter coordinates were reused with different immutable content: partition "
            + reference.partition()
            + " offset "
            + reference.offset());
  }
}
