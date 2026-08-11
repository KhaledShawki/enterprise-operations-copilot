package io.github.khaledshawki.eoc.analytics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record AnalyticsMoney(BigDecimal amount, CurrencyCode currency)
    implements Comparable<AnalyticsMoney> {

  public AnalyticsMoney {
    Objects.requireNonNull(amount, "Analytics money amount cannot be null");
    Objects.requireNonNull(currency, "Analytics money currency cannot be null");
    try {
      amount = amount.setScale(currency.fractionDigits(), RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Analytics money amount has unsupported precision for currency " + currency.value(),
          exception);
    }
  }

  public static AnalyticsMoney of(BigDecimal amount, String currency) {
    return new AnalyticsMoney(amount, CurrencyCode.of(currency));
  }

  public AnalyticsMoney subtract(AnalyticsMoney other) {
    requireSameCurrency(other);
    return new AnalyticsMoney(amount.subtract(other.amount), currency);
  }

  @Override
  public int compareTo(AnalyticsMoney other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  private void requireSameCurrency(AnalyticsMoney other) {
    Objects.requireNonNull(other, "Analytics money operand cannot be null");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Analytics money currencies must match");
    }
  }
}
