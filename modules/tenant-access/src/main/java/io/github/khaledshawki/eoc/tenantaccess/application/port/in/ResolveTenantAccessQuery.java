package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record ResolveTenantAccessQuery(
    String issuer, String subject, UUID tenantId, String requiredRole) {

  public ResolveTenantAccessQuery {
    Objects.requireNonNull(issuer, "Issuer cannot be null");
    Objects.requireNonNull(subject, "Subject cannot be null");
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(requiredRole, "Required tenant role cannot be null");

    if (issuer.isBlank()) {
      throw new IllegalArgumentException("Issuer cannot be blank");
    }

    if (subject.isBlank()) {
      throw new IllegalArgumentException("Subject cannot be blank");
    }

    if (requiredRole.isBlank()) {
      throw new IllegalArgumentException("Required tenant role cannot be blank");
    }
  }
}
