package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import java.util.Objects;

public final class PlatformUserNotFoundException extends RuntimeException {

  public PlatformUserNotFoundException(PlatformUserId platformUserId) {
    super(
        "Platform user "
            + Objects.requireNonNull(platformUserId, "Platform user id cannot be null").value()
            + " was not found");
  }

  public PlatformUserNotFoundException(ExternalIdentity externalIdentity) {
    super(message(externalIdentity));
  }

  private static String message(ExternalIdentity externalIdentity) {
    Objects.requireNonNull(externalIdentity, "External identity cannot be null");

    return "Platform user with external identity was not found";
  }
}
