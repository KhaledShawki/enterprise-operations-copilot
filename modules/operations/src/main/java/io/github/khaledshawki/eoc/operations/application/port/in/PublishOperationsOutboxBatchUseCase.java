package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchResult;

@FunctionalInterface
public interface PublishOperationsOutboxBatchUseCase {

  PublishOperationsOutboxBatchResult publishBatch(PublishOperationsOutboxBatchCommand command);
}
