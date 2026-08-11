package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPage;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterPartition;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterRecord;
import io.github.khaledshawki.eoc.connectormanagement.application.model.recovery.ConnectorDeadLetterReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InspectConnectorDeadLettersServiceTest {

  @Test
  void enforcesTheApplicationPageBoundBeforeCallingKafka() {
    InspectConnectorDeadLettersService service =
        new InspectConnectorDeadLettersService(new EmptyReader(), 100);

    assertThrows(IllegalArgumentException.class, () -> service.list(0, 0, 101));
    assertThrows(IllegalArgumentException.class, () -> service.list(-1, 0, 1));
  }

  @Test
  void turnsAnAbsentRetainedRecordIntoAStableNotFoundFailure() {
    InspectConnectorDeadLettersService service =
        new InspectConnectorDeadLettersService(new EmptyReader(), 100);
    ConnectorDeadLetterReference reference = new ConnectorDeadLetterReference(1, 9);

    ConnectorDeadLetterNotFoundException exception =
        assertThrows(ConnectorDeadLetterNotFoundException.class, () -> service.get(reference));

    assertEquals("Connector dead letter not found at partition 1 offset 9", exception.getMessage());
  }

  private static final class EmptyReader implements ConnectorDeadLetterReader {

    @Override
    public List<ConnectorDeadLetterPartition> listPartitions() {
      return List.of();
    }

    @Override
    public ConnectorDeadLetterPage readPage(int partition, long fromOffset, int limit) {
      return new ConnectorDeadLetterPage(partition, fromOffset, fromOffset, fromOffset, List.of());
    }

    @Override
    public Optional<ConnectorDeadLetterRecord> find(ConnectorDeadLetterReference reference) {
      return Optional.empty();
    }
  }
}
