package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;

public final class PlatformUserNotActiveException extends RuntimeException {

  public PlatformUserNotActiveException(PlatformUserId platformUserId) {
    super("Platform user " + platformUserId.value() + " is not active");
  }
}
