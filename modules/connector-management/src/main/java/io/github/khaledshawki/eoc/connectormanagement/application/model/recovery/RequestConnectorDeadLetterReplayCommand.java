package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import java.util.Objects;

public record RequestConnectorDeadLetterReplayCommand(
    ConnectorActor actor, ConnectorDeadLetterReference deadLetter, String reason) {

  public RequestConnectorDeadLetterReplayCommand {
    Objects.requireNonNull(actor, "Replay request actor cannot be null");
    Objects.requireNonNull(deadLetter, "Replay request dead-letter reference cannot be null");
    reason = ConnectorDeadLetterReplayRequest.requireText(reason, "Replay reason", 500);
  }
}
