package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;

public record ListAccessibleTenantsQuery(String issuer, String subject) {

  public ListAccessibleTenantsQuery {
    Objects.requireNonNull(issuer, "Issuer cannot be null");

    Objects.requireNonNull(subject, "Subject cannot be null");

    if (issuer.isBlank()) {
      throw new IllegalArgumentException("Issuer cannot be blank");
    }

    if (subject.isBlank()) {
      throw new IllegalArgumentException("Subject cannot be blank");
    }
  }
}
