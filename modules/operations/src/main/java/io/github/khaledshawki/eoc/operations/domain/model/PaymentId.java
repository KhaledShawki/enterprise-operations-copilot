package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(UUID value) {

  public PaymentId {
    Objects.requireNonNull(value, "Payment id cannot be null");
  }

  public static PaymentId generate() {
    return new PaymentId(UUID.randomUUID());
  }

  public static PaymentId of(UUID value) {
    return new PaymentId(value);
  }
}
