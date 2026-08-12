package io.github.khaledshawki.eoc.analytics.application.model.querying;

import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import java.util.Objects;

public record ReceivableSnapshot(
    InvoiceReceivableProjection invoice, ReceivableCustomerSummary customer) {

  public ReceivableSnapshot {
    Objects.requireNonNull(invoice, "Receivable invoice projection cannot be null");
    Objects.requireNonNull(customer, "Receivable customer summary cannot be null");
    if (!invoice.customerId().equals(customer.customerId())) {
      throw new IllegalArgumentException(
          "Receivable customer summary does not belong to the invoice projection");
    }
  }
}
