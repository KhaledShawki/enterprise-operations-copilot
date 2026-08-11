package io.github.khaledshawki.eoc.operations.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.outbox.NewOperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventCommand;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoverOperationsOutboxEventServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-11T14:30:00Z");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000811");
  private static final UUID RECOVERY_ID = UUID.fromString("00000000-0000-0000-0000-000000000812");
  private static final OperationsActor ACTOR =
      new OperationsActor("http://localhost:8180/realms/eoc", "platform-admin-1");

  @Test
  void shouldCreateAStableAuditedRecoveryRequest() {
    RecordingRepository repository = new RecordingRepository();
    RecoverOperationsOutboxEventService service =
        new RecoverOperationsOutboxEventService(
            repository, () -> RECOVERY_ID, Clock.fixed(NOW, ZoneOffset.UTC));

    OperationsOutboxRecovery result =
        service.recover(
            new RecoverOperationsOutboxEventCommand(
                ACTOR, EVENT_ID, "  broker configuration corrected  "));

    NewOperationsOutboxRecovery request = repository.request;
    assertEquals(RECOVERY_ID, request.recoveryId());
    assertSame(ACTOR, request.actor());
    assertEquals(EVENT_ID, request.eventId());
    assertEquals("broker configuration corrected", request.reason());
    assertEquals(NOW, request.requestedAt());
    assertSame(repository.result, result);
  }

  private static final class RecordingRepository implements OperationsOutboxRecoveryRepository {

    private NewOperationsOutboxRecovery request;
    private final OperationsOutboxRecovery result =
        new OperationsOutboxRecovery(
            RECOVERY_ID,
            EVENT_ID,
            1,
            ACTOR.issuer(),
            ACTOR.subject(),
            "broker configuration corrected",
            OperationsOutboxStatus.FAILED,
            3,
            3,
            "broker-unavailable",
            NOW,
            NOW);

    @Override
    public OperationsOutboxRecovery recover(NewOperationsOutboxRecovery recovery) {
      request = recovery;
      return result;
    }
  }
}
