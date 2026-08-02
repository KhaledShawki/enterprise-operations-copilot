package io.github.khaledshawki.eoc.connectormanagement.application.model.authorization;

import java.util.Objects;

/** Framework-independent identity of the actor invoking a connector use case. */
public record ConnectorActor(String issuer, String subject) {

  public ConnectorActor {
    Objects.requireNonNull(issuer, "Connector actor issuer cannot be null");
    Objects.requireNonNull(subject, "Connector actor subject cannot be null");

    if (issuer.isBlank()) {
      throw new IllegalArgumentException("Connector actor issuer cannot be blank");
    }

    if (subject.isBlank()) {
      throw new IllegalArgumentException("Connector actor subject cannot be blank");
    }
  }
}
