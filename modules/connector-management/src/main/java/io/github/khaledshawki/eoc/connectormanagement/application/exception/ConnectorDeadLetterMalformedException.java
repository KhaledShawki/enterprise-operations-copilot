package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;

public final class ConnectorDeadLetterMalformedException extends RuntimeException {

  public ConnectorDeadLetterMalformedException(
      ConnectorDeadLetterReference reference, String reason) {
    super(
        "Connector dead-letter record at partition "
            + reference.partition()
            + ", offset "
            + reference.offset()
            + " is not replayable: "
            + reason);
  }
}
