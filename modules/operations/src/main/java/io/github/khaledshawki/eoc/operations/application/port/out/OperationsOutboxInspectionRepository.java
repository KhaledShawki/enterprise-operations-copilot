package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecoveryPage;
import java.util.Optional;
import java.util.UUID;

public interface OperationsOutboxInspectionRepository {

  OperationsOutboxPage list(OperationsOutboxInspectionFilter filter);

  Optional<OperationsOutboxEventView> findById(UUID eventId);

  OperationsOutboxRecoveryPage listRecoveries(
      UUID eventId, Optional<Integer> beforeGeneration, int limit);
}
