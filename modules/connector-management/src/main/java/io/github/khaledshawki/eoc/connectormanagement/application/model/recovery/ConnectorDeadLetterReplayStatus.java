package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

public enum ConnectorDeadLetterReplayStatus {
  PENDING,
  CLAIMED,
  RETRY_SCHEDULED,
  REPLAYED,
  FAILED
}
