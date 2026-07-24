package io.github.khaledshawki.eoc.tenantaccess.application.exception;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;

public final class ExternalIdentityAlreadyExistsException extends RuntimeException {

  public ExternalIdentityAlreadyExistsException(ExternalIdentity identity) {
    super(message(identity));
  }

  public ExternalIdentityAlreadyExistsException(ExternalIdentity identity, Throwable cause) {
    super(message(identity), cause);
  }

  private static String message(ExternalIdentity identity) {
    return "External identity with issuer "
        + identity.issuer()
        + " and subject "
        + identity.subject()
        + " already exists";
  }
}
