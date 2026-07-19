package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public record ExternalIdentity(String issuer, String subject) {

  public ExternalIdentity {
    Objects.requireNonNull(issuer, "External identity issuer cannot be null");
    Objects.requireNonNull(subject, "External identity subject cannot be null");

    if (issuer.isBlank()) {
      throw new IllegalArgumentException("External identity issuer cannot be blank");
    }

    if (subject.isBlank()) {
      throw new IllegalArgumentException("External identity subject cannot be blank");
    }
  }

  public static ExternalIdentity of(String issuer, String subject) {
    return new ExternalIdentity(issuer, subject);
  }
}
