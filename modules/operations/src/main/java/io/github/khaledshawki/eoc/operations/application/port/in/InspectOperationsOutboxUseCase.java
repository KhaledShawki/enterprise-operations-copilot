package io.github.khaledshawki.eoc.operations.application.port.in;

import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecoveryPage;
import java.util.Optional;
import java.util.UUID;

public interface InspectOperationsOutboxUseCase {

  OperationsOutboxPage list(OperationsOutboxInspectionFilter filter);

  OperationsOutboxEventView get(UUID eventId);

  OperationsOutboxRecoveryPage listRecoveries(
      UUID eventId, Optional<Integer> beforeGeneration, int limit);
}
