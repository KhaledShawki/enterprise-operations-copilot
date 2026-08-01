package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CredentialReference(UUID value) {

  public CredentialReference {
    Objects.requireNonNull(value, "Credential reference cannot be null");
  }

  public static CredentialReference of(UUID value) {
    return new CredentialReference(value);
  }
}
