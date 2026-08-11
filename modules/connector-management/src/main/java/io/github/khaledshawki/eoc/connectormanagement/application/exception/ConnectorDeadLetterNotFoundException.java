package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;

public final class ConnectorDeadLetterNotFoundException extends RuntimeException {

  public ConnectorDeadLetterNotFoundException(ConnectorDeadLetterReference reference) {
    super(
        "Connector dead letter not found at partition "
            + reference.partition()
            + " offset "
            + reference.offset());
  }
}
