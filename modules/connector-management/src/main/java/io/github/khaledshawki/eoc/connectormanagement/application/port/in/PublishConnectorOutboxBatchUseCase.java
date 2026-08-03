package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;

public interface PublishConnectorOutboxBatchUseCase {

  PublishConnectorOutboxBatchResult publishBatch(PublishConnectorOutboxBatchCommand command);
}
