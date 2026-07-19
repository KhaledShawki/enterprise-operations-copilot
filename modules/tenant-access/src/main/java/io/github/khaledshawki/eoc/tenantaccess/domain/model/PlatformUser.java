package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public final class PlatformUser {

  private final PlatformUserId id;
  private final ExternalIdentity externalIdentity;
  private PlatformUserStatus status;

  private PlatformUser(
      PlatformUserId id, ExternalIdentity externalIdentity, PlatformUserStatus status) {
    this.id = Objects.requireNonNull(id, "Platform user id cannot be null");
    this.externalIdentity =
        Objects.requireNonNull(externalIdentity, "External identity cannot be null");
    this.status = Objects.requireNonNull(status, "Platform user status cannot be null");
  }

  public static PlatformUser create(ExternalIdentity externalIdentity) {
    return new PlatformUser(PlatformUserId.generate(), externalIdentity, PlatformUserStatus.ACTIVE);
  }

  public static PlatformUser reconstitute(
      PlatformUserId id, ExternalIdentity externalIdentity, PlatformUserStatus status) {
    return new PlatformUser(id, externalIdentity, status);
  }

  public void suspend() {
    if (status == PlatformUserStatus.SUSPENDED) {
      throw new IllegalStateException("Platform user is already suspended");
    }

    status = PlatformUserStatus.SUSPENDED;
  }

  public void activate() {
    if (status == PlatformUserStatus.ACTIVE) {
      throw new IllegalStateException("Platform user is already active");
    }

    status = PlatformUserStatus.ACTIVE;
  }

  public PlatformUserId id() {
    return id;
  }

  public ExternalIdentity externalIdentity() {
    return externalIdentity;
  }

  public PlatformUserStatus status() {
    return status;
  }
}
