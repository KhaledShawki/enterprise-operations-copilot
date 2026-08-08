package io.github.khaledshawki.eoc.operations.application.exception;

import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import java.util.Objects;

public final class ReceivableAllocationReplayConflictException extends RuntimeException {

  public ReceivableAllocationReplayConflictException(
      ReceivableAllocationId allocationId, String reason) {
    super(
        "Receivable allocation "
            + Objects.requireNonNull(allocationId, "Receivable allocation id cannot be null")
                .value()
            + " conflicts with the requested mutation: "
            + requireReason(reason));
  }

  private static String requireReason(String reason) {
    Objects.requireNonNull(reason, "Replay conflict reason cannot be null");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("Replay conflict reason cannot be blank");
    }
    return reason;
  }
}
