package io.github.khaledshawki.eoc.operations.application.model.authorization;

import java.util.Objects;

/** Framework-independent identity of an actor invoking an Operations use case. */
public record OperationsActor(String issuer, String subject) {

  public OperationsActor {
    Objects.requireNonNull(issuer, "Operations actor issuer cannot be null");
    Objects.requireNonNull(subject, "Operations actor subject cannot be null");
    if (issuer.isBlank()) {
      throw new IllegalArgumentException("Operations actor issuer cannot be blank");
    }
    if (subject.isBlank()) {
      throw new IllegalArgumentException("Operations actor subject cannot be blank");
    }
  }
}
