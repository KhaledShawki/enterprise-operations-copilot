package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;

@FunctionalInterface
public interface ConnectorDeadLetterReplayPublisher {

  void publish(ClaimedConnectorDeadLetterReplay replay);
}
