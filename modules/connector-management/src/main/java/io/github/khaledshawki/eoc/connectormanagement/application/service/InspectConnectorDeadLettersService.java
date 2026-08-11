package io.github.khaledshawki.eoc.connectormanagement.application.service;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import java.util.List;
import java.util.Objects;

public final class InspectConnectorDeadLettersService
    implements InspectConnectorDeadLettersUseCase {

  private final ConnectorDeadLetterReader reader;
  private final int maxPageSize;

  public InspectConnectorDeadLettersService(ConnectorDeadLetterReader reader, int maxPageSize) {
    this.reader = Objects.requireNonNull(reader, "Connector dead-letter reader cannot be null");
    if (maxPageSize < 1 || maxPageSize > 1000) {
      throw new IllegalArgumentException(
          "Dead-letter maximum page size must be between 1 and 1000");
    }
    this.maxPageSize = maxPageSize;
  }

  @Override
  public List<ConnectorDeadLetterPartition> listPartitions() {
    return List.copyOf(reader.listPartitions());
  }

  @Override
  public ConnectorDeadLetterPage list(int partition, long fromOffset, int limit) {
    new ConnectorDeadLetterReference(partition, fromOffset);
    if (limit < 1 || limit > maxPageSize) {
      throw new IllegalArgumentException(
          "Dead-letter page size must be between 1 and " + maxPageSize);
    }
    return reader.readPage(partition, fromOffset, limit);
  }

  @Override
  public ConnectorDeadLetterRecord get(ConnectorDeadLetterReference reference) {
    Objects.requireNonNull(reference, "Dead-letter reference cannot be null");
    return reader
        .find(reference)
        .orElseThrow(() -> new ConnectorDeadLetterNotFoundException(reference));
  }
}
