package io.github.khaledshawki.eoc.analytics.application.model.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public sealed interface AnalyticsProjectionPayload
    permits AnalyticsProjectionPayload.BusinessPartner,
        AnalyticsProjectionPayload.InvoiceReceivable,
        AnalyticsProjectionPayload.Ignored {

  record BusinessPartner(
      UUID businessPartnerId, String partnerNumber, String displayName, Set<String> roles)
      implements AnalyticsProjectionPayload {

    public BusinessPartner {
      Objects.requireNonNull(
          businessPartnerId, "Analytics event business partner id cannot be null");
      Objects.requireNonNull(partnerNumber, "Analytics event partner number cannot be null");
      Objects.requireNonNull(displayName, "Analytics event display name cannot be null");
      Objects.requireNonNull(roles, "Analytics event business partner roles cannot be null");
      roles = Set.copyOf(roles);
    }
  }

  record InvoiceReceivable(
      UUID invoiceId,
      UUID customerId,
      String invoiceNumber,
      BigDecimal originalAmount,
      BigDecimal paidAmount,
      String currency,
      LocalDate issueDate,
      LocalDate dueDate,
      boolean cancelled,
      String status)
      implements AnalyticsProjectionPayload {

    public InvoiceReceivable {
      Objects.requireNonNull(invoiceId, "Analytics event invoice id cannot be null");
      Objects.requireNonNull(customerId, "Analytics event customer id cannot be null");
      Objects.requireNonNull(invoiceNumber, "Analytics event invoice number cannot be null");
      Objects.requireNonNull(originalAmount, "Analytics event original amount cannot be null");
      Objects.requireNonNull(paidAmount, "Analytics event paid amount cannot be null");
      Objects.requireNonNull(currency, "Analytics event currency cannot be null");
      Objects.requireNonNull(issueDate, "Analytics event issue date cannot be null");
      Objects.requireNonNull(dueDate, "Analytics event due date cannot be null");
      Objects.requireNonNull(status, "Analytics event invoice status cannot be null");
    }
  }

  record Ignored() implements AnalyticsProjectionPayload {}
}
