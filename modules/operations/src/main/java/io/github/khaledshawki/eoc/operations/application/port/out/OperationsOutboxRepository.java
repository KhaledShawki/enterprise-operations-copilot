package io.github.khaledshawki.eoc.operations.application.port.out;

import io.github.khaledshawki.eoc.operations.application.model.outbox.ClaimedOperationsOutboxEvent;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxClaim;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationFailure;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationRetry;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPublicationSuccess;
import java.util.List;

public interface OperationsOutboxRepository {

  List<ClaimedOperationsOutboxEvent> claimPublishable(OperationsOutboxClaim claim);

  void markPublished(OperationsOutboxPublicationSuccess success);

  void scheduleRetry(OperationsOutboxPublicationRetry retry);

  void markFailed(OperationsOutboxPublicationFailure failure);
}
