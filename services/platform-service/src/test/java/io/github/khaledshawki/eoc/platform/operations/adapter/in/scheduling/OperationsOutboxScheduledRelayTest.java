package io.github.khaledshawki.eoc.platform.operations.adapter.in.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchCommand;
import io.github.khaledshawki.eoc.operations.application.model.outbox.PublishOperationsOutboxBatchResult;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationsOutboxScheduledRelayTest {

  @Test
  void entersThroughTheOperationsInputPortWithAStableWorkerCommand() {
    RecordingUseCase useCase = new RecordingUseCase();
    OperationsOutboxScheduledRelay relay =
        new OperationsOutboxScheduledRelay(
            useCase, "operations-worker-1", 7, Duration.ofSeconds(31));

    relay.publishNextBatch();
    relay.publishNextBatch();

    PublishOperationsOutboxBatchCommand expected =
        new PublishOperationsOutboxBatchCommand("operations-worker-1", 7, Duration.ofSeconds(31));
    assertEquals(List.of(expected, expected), useCase.commands);
  }

  private static final class RecordingUseCase implements PublishOperationsOutboxBatchUseCase {

    private final List<PublishOperationsOutboxBatchCommand> commands = new ArrayList<>();

    @Override
    public PublishOperationsOutboxBatchResult publishBatch(
        PublishOperationsOutboxBatchCommand command) {
      commands.add(command);
      return PublishOperationsOutboxBatchResult.empty();
    }
  }
}
