package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import java.util.UUID;

public final class ConnectorDeadLetterReplayNotFoundException extends RuntimeException {

  public ConnectorDeadLetterReplayNotFoundException(UUID requestId) {
    super("Connector dead-letter replay request not found: " + requestId);
  }
}
