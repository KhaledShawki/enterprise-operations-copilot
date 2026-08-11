package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.PublishConnectorDeadLetterReplayBatchResult;

@FunctionalInterface
public interface PublishConnectorDeadLetterReplayBatchUseCase {

  PublishConnectorDeadLetterReplayBatchResult publishBatch(
      PublishConnectorDeadLetterReplayBatchCommand command);
}
