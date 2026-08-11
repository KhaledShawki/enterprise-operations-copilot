package io.github.khaledshawki.eoc.connectormanagement.application.exception;

import java.util.UUID;

public final class ConnectorDeadLetterReplayClaimLostException extends RuntimeException {

  public ConnectorDeadLetterReplayClaimLostException(
      UUID requestId, String claimOwner, int publicationAttempt) {
    super(
        "Connector dead-letter replay claim was lost for request "
            + requestId
            + ", owner "
            + claimOwner
            + ", attempt "
            + publicationAttempt);
  }
}
