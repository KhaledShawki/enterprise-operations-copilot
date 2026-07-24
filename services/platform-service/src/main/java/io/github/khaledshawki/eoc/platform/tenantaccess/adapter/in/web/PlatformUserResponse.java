package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserResult;
import java.util.Objects;
import java.util.UUID;

public record PlatformUserResponse(UUID id, String issuer, String subject, String status) {

  static PlatformUserResponse from(ProvisionPlatformUserResult result) {
    Objects.requireNonNull(result, "Provision platform user result cannot be null");

    return new PlatformUserResponse(
        result.userId().value(),
        result.externalIdentity().issuer(),
        result.externalIdentity().subject(),
        result.status().name());
  }
}
