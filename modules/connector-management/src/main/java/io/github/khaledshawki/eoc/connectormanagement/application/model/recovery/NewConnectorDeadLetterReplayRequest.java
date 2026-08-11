package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NewConnectorDeadLetterReplayRequest(
    UUID requestId,
    ConnectorDeadLetterRecord deadLetter,
    String recordFingerprint,
    String requestedByIssuer,
    String requestedBySubject,
    String reason,
    Instant requestedAt) {

  public NewConnectorDeadLetterReplayRequest {
    Objects.requireNonNull(requestId, "Replay request id cannot be null");
    Objects.requireNonNull(deadLetter, "Replay request dead letter cannot be null");
    recordFingerprint =
        ConnectorDeadLetterReplayRequest.requireText(
            recordFingerprint, "Replay record fingerprint", 64);
    requestedByIssuer =
        ConnectorDeadLetterReplayRequest.requireText(
            requestedByIssuer, "Replay requester issuer", 500);
    requestedBySubject =
        ConnectorDeadLetterReplayRequest.requireText(
            requestedBySubject, "Replay requester subject", 255);
    reason = ConnectorDeadLetterReplayRequest.requireText(reason, "Replay reason", 500);
    Objects.requireNonNull(requestedAt, "Replay request timestamp cannot be null");
  }

  public int replayGeneration() {
    return deadLetter.replayGeneration() + 1;
  }
}
