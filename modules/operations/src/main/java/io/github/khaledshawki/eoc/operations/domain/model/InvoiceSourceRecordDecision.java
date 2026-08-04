package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Objects;

public record InvoiceSourceRecordDecision(
    SourceRecordAcceptance acceptance, InvoiceSourceMapping resultingMapping) {

  public InvoiceSourceRecordDecision {
    Objects.requireNonNull(acceptance, "Source record acceptance cannot be null");
    Objects.requireNonNull(resultingMapping, "Resulting invoice source mapping cannot be null");
  }

  public static InvoiceSourceRecordDecision accepted(InvoiceSourceMapping resultingMapping) {
    return new InvoiceSourceRecordDecision(SourceRecordAcceptance.ACCEPTED, resultingMapping);
  }

  public static InvoiceSourceRecordDecision duplicate(InvoiceSourceMapping currentMapping) {
    return new InvoiceSourceRecordDecision(SourceRecordAcceptance.DUPLICATE, currentMapping);
  }

  public static InvoiceSourceRecordDecision stale(InvoiceSourceMapping currentMapping) {
    return new InvoiceSourceRecordDecision(SourceRecordAcceptance.STALE, currentMapping);
  }
}
