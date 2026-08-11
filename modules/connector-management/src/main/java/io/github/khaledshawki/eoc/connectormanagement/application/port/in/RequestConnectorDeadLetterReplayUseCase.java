package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReplayRequest;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.RequestConnectorDeadLetterReplayCommand;
import java.util.UUID;

public interface RequestConnectorDeadLetterReplayUseCase {

  ConnectorDeadLetterReplayRequest request(RequestConnectorDeadLetterReplayCommand command);

  ConnectorDeadLetterReplayRequest get(UUID requestId);
}
