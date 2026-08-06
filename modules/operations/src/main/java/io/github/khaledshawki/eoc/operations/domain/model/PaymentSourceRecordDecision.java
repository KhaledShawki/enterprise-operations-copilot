package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;

public record PaymentSourceRecordDecision(
    SourceRecordAcceptance acceptance, PaymentSourceMapping resultingMapping) {

  public PaymentSourceRecordDecision {
    Objects.requireNonNull(acceptance, "Source record acceptance cannot be null");
    Objects.requireNonNull(resultingMapping, "Resulting payment source mapping cannot be null");
  }

  public static PaymentSourceRecordDecision accepted(PaymentSourceMapping resultingMapping) {
    return new PaymentSourceRecordDecision(SourceRecordAcceptance.ACCEPTED, resultingMapping);
  }

  public static PaymentSourceRecordDecision duplicate(PaymentSourceMapping currentMapping) {
    return new PaymentSourceRecordDecision(SourceRecordAcceptance.DUPLICATE, currentMapping);
  }

  public static PaymentSourceRecordDecision stale(PaymentSourceMapping currentMapping) {
    return new PaymentSourceRecordDecision(SourceRecordAcceptance.STALE, currentMapping);
  }
}
