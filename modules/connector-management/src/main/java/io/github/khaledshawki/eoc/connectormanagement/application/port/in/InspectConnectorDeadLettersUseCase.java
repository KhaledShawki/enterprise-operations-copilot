package io.github.khaledshawki.eoc.connectormanagement.application.port.in;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import java.util.List;

public interface InspectConnectorDeadLettersUseCase {

  List<ConnectorDeadLetterPartition> listPartitions();

  ConnectorDeadLetterPage list(int partition, long fromOffset, int limit);

  ConnectorDeadLetterRecord get(ConnectorDeadLetterReference reference);
}
