package io.github.khaledshawki.eoc.analytics.domain.model;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record CurrencyCode(String value) {

  public CurrencyCode {
    Objects.requireNonNull(value, "Currency code cannot be null");
    value = value.strip().toUpperCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Currency code cannot be blank");
    }

    Currency currency;
    try {
      currency = Currency.getInstance(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Currency code must be a valid ISO 4217 code", exception);
    }
    if (currency.getDefaultFractionDigits() < 0) {
      throw new IllegalArgumentException("Currency code must define monetary fraction digits");
    }
  }

  public static CurrencyCode of(String value) {
    return new CurrencyCode(value);
  }

  public int fractionDigits() {
    return Currency.getInstance(value).getDefaultFractionDigits();
  }
}
