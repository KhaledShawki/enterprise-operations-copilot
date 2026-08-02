package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record BusinessPartnerId(UUID value) {

  public BusinessPartnerId {
    Objects.requireNonNull(value, "Business partner id cannot be null");
  }

  public static BusinessPartnerId generate() {
    return new BusinessPartnerId(UUID.randomUUID());
  }

  public static BusinessPartnerId of(UUID value) {
    return new BusinessPartnerId(value);
  }
}
