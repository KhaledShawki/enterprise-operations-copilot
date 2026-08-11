package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.model.outbox.NewOperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecovery;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RecoverOperationsOutboxEventService
    implements RecoverOperationsOutboxEventUseCase {

  private final OperationsOutboxRecoveryRepository repository;
  private final Supplier<UUID> recoveryIdGenerator;
  private final Clock clock;

  public RecoverOperationsOutboxEventService(
      OperationsOutboxRecoveryRepository repository,
      Supplier<UUID> recoveryIdGenerator,
      Clock clock) {
    this.repository =
        Objects.requireNonNull(repository, "Operations outbox recovery repository cannot be null");
    this.recoveryIdGenerator =
        Objects.requireNonNull(
            recoveryIdGenerator, "Operations recovery id generator cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public OperationsOutboxRecovery recover(RecoverOperationsOutboxEventCommand command) {
    Objects.requireNonNull(command, "Operations outbox recovery command cannot be null");
    UUID recoveryId =
        Objects.requireNonNull(
            recoveryIdGenerator.get(), "Generated Operations recovery id cannot be null");
    return Objects.requireNonNull(
        repository.recover(
            new NewOperationsOutboxRecovery(
                recoveryId, command.actor(), command.eventId(), command.reason(), clock.instant())),
        "Operations outbox recovery repository returned null recovery");
  }
}
