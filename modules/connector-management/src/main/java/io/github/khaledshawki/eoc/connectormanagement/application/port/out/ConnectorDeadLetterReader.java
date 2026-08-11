package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import java.util.List;
import java.util.Optional;

public interface ConnectorDeadLetterReader {

  List<ConnectorDeadLetterPartition> listPartitions();

  ConnectorDeadLetterPage readPage(int partition, long fromOffset, int limit);

  Optional<ConnectorDeadLetterRecord> find(ConnectorDeadLetterReference reference);
}
