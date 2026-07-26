package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;

public final class PlatformUserNotFoundException extends RuntimeException {

  public PlatformUserNotFoundException(PlatformUserId platformUserId) {
    super("Platform user " + platformUserId.value() + " was not found");
  }
}
