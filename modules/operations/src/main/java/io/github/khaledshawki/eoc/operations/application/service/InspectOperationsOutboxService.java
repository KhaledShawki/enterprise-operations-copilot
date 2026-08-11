package io.github.khaledshawki.eoc.operations.application.service;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxEventNotFoundException;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxEventView;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxInspectionFilter;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxPage;
import io.github.khaledshawki.eoc.operations.application.model.outbox.OperationsOutboxRecoveryPage;
import io.github.khaledshawki.eoc.operations.application.port.in.InspectOperationsOutboxUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxInspectionRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InspectOperationsOutboxService implements InspectOperationsOutboxUseCase {

  private final OperationsOutboxInspectionRepository repository;

  public InspectOperationsOutboxService(OperationsOutboxInspectionRepository repository) {
    this.repository =
        Objects.requireNonNull(
            repository, "Operations outbox inspection repository cannot be null");
  }

  @Override
  public OperationsOutboxPage list(OperationsOutboxInspectionFilter filter) {
    Objects.requireNonNull(filter, "Operations outbox inspection filter cannot be null");
    return Objects.requireNonNull(
        repository.list(filter), "Operations outbox inspection repository returned null page");
  }

  @Override
  public OperationsOutboxEventView get(UUID eventId) {
    Objects.requireNonNull(eventId, "Operations outbox event id cannot be null");
    Optional<OperationsOutboxEventView> lookup = repository.findById(eventId);
    if (lookup == null) {
      throw new IllegalStateException(
          "Operations outbox inspection repository returned null lookup");
    }
    return lookup.orElseThrow(() -> new OperationsOutboxEventNotFoundException(eventId));
  }

  @Override
  public OperationsOutboxRecoveryPage listRecoveries(
      UUID eventId, Optional<Integer> beforeGeneration, int limit) {
    Objects.requireNonNull(eventId, "Operations outbox recovery event id cannot be null");
    Objects.requireNonNull(beforeGeneration, "Operations outbox recovery cursor cannot be null");
    beforeGeneration.ifPresent(
        generation -> {
          if (generation < 1) {
            throw new IllegalArgumentException("Recovery cursor generation must be positive");
          }
        });
    if (limit < 1 || limit > OperationsOutboxInspectionFilter.MAX_LIMIT) {
      throw new IllegalArgumentException(
          "Operations outbox recovery limit must be between 1 and 100");
    }
    get(eventId);
    return Objects.requireNonNull(
        repository.listRecoveries(eventId, beforeGeneration, limit),
        "Operations outbox inspection repository returned null recovery page");
  }
}
