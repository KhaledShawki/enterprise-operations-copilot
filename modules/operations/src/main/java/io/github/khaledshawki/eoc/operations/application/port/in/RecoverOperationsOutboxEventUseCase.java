package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;

public interface RecoverOperationsOutboxEventUseCase {

  OperationsOutboxRecovery recover(RecoverOperationsOutboxEventCommand command);
}
