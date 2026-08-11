package io.github.khaledshawki.eoc.operations.application.model.outbox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OperationsOutboxRecoveryPage(
    List<OperationsOutboxRecovery> recoveries, Optional<Integer> nextBeforeGeneration) {

  public OperationsOutboxRecoveryPage {
    Objects.requireNonNull(recoveries, "Operations outbox recovery page cannot be null");
    if (recoveries.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException(
          "Operations outbox recovery page cannot contain null recoveries");
    }
    recoveries = List.copyOf(recoveries);
    Objects.requireNonNull(
        nextBeforeGeneration, "Operations outbox recovery next generation cannot be null");
    nextBeforeGeneration.ifPresent(
        generation -> {
          if (generation < 1) {
            throw new IllegalArgumentException("Recovery cursor generation must be positive");
          }
        });
  }
}
