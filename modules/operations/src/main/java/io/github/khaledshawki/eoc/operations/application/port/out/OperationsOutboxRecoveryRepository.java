package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.outbox.NewOperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;

public interface OperationsOutboxRecoveryRepository {

  OperationsOutboxRecovery recover(NewOperationsOutboxRecovery recovery);
}
