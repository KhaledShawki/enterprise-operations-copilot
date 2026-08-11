package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ClaimedConnectorDeadLetterReplay;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayClaim;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayFailure;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRetry;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplaySuccess;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.NewConnectorDeadLetterReplayRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorDeadLetterReplayRepository {

  ConnectorDeadLetterReplayRequest request(NewConnectorDeadLetterReplayRequest request);

  Optional<ConnectorDeadLetterReplayRequest> findById(UUID requestId);

  List<ClaimedConnectorDeadLetterReplay> claimPublishable(ConnectorDeadLetterReplayClaim claim);

  void markReplayed(ConnectorDeadLetterReplaySuccess success);

  void scheduleRetry(ConnectorDeadLetterReplayRetry retry);

  void markFailed(ConnectorDeadLetterReplayFailure failure);
}
