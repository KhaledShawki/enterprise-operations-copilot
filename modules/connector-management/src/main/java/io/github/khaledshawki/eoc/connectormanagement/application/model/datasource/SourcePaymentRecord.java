package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Normalized customer-payment data returned by a source adapter, not an owned business aggregate.
 */
public record SourcePaymentRecord(
    SourceRecordMetadata metadata,
    SourceIdentity customerIdentity,
    LocalDate paymentDate,
    Currency currency,
    BigDecimal amount,
    boolean reversed) {

  public SourcePaymentRecord {
    Objects.requireNonNull(metadata, "Payment source metadata cannot be null");
    Objects.requireNonNull(customerIdentity, "Payment customer identity cannot be null");
    if (!SourceEntity.PAYMENT.equals(metadata.identity().entity())) {
      throw new IllegalArgumentException("Payment source identity must use the payment entity");
    }
    if (!SourceEntity.CUSTOMER.equals(customerIdentity.entity())) {
      throw new IllegalArgumentException("Payment customer identity must use the customer entity");
    }
    Objects.requireNonNull(paymentDate, "Payment date cannot be null");
    Objects.requireNonNull(currency, "Payment currency cannot be null");
    Objects.requireNonNull(amount, "Payment amount cannot be null");
  }
}
