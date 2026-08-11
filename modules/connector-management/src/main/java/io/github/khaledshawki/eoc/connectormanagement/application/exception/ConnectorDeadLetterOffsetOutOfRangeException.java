package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;

public final class ConnectorDeadLetterOffsetOutOfRangeException extends RuntimeException {

  public ConnectorDeadLetterOffsetOutOfRangeException(
      ConnectorDeadLetterReference reference, long beginningOffset, long endOffset) {
    super(
        "Connector dead-letter offset "
            + reference.offset()
            + " in partition "
            + reference.partition()
            + " is outside retained range ["
            + beginningOffset
            + ", "
            + endOffset
            + ")");
  }
}
