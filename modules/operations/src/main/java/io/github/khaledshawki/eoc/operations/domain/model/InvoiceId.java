package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record InvoiceId(UUID value) {

  public InvoiceId {
    Objects.requireNonNull(value, "Invoice id cannot be null");
  }

  public static InvoiceId generate() {
    return new InvoiceId(UUID.randomUUID());
  }

  public static InvoiceId of(UUID value) {
    return new InvoiceId(value);
  }
}
