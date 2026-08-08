package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ReceivableSettlementId(UUID value) {

  public ReceivableSettlementId {
    Objects.requireNonNull(value, "Receivable settlement id cannot be null");
  }

  public static ReceivableSettlementId generate() {
    return new ReceivableSettlementId(UUID.randomUUID());
  }

  public static ReceivableSettlementId of(UUID value) {
    return new ReceivableSettlementId(value);
  }
}
