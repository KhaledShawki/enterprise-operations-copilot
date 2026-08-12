package io.github.khaledshawki.eoc.copilot.application.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record CopilotMoney(BigDecimal amount, String currency) {
  public CopilotMoney {
    Objects.requireNonNull(amount, "Copilot money amount cannot be null");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Copilot money amount cannot be negative");
    }
    Objects.requireNonNull(currency, "Copilot money currency cannot be null");
    currency = currency.strip();
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException(
          "Copilot money currency must be an uppercase ISO 4217 code");
    }
    Currency isoCurrency;
    try {
      isoCurrency = Currency.getInstance(currency);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Copilot money currency must be a valid ISO 4217 code", exception);
    }
    if (isoCurrency.getDefaultFractionDigits() < 0
        || amount.scale() != isoCurrency.getDefaultFractionDigits()) {
      throw new IllegalArgumentException(
          "Copilot money amount must use the currency fraction digits");
    }
  }
}
