package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ClaimedConnectorOutboxEvent;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.ConnectorOutboxPublicationSuccess;
import java.util.List;

public interface ConnectorOutboxRepository {

  List<ClaimedConnectorOutboxEvent> claimPublishable(ConnectorOutboxClaim claim);

  void markPublished(ConnectorOutboxPublicationSuccess success);

  void scheduleRetry(ConnectorOutboxPublicationRetry retry);

  void markFailed(ConnectorOutboxPublicationFailure failure);
}
