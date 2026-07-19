package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PlatformUserId(UUID value) {

  public PlatformUserId {
    Objects.requireNonNull(value, "Platform user id cannot be null");
  }

  public static PlatformUserId of(UUID value) {
    return new PlatformUserId(value);
  }

  public static PlatformUserId generate() {
    return new PlatformUserId(UUID.randomUUID());
  }
}
