package io.github.khaledshawki.eoc.operations.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, CurrencyCode currency) implements Comparable<Money> {

  public Money {
    Objects.requireNonNull(amount, "Money amount cannot be null");
    Objects.requireNonNull(currency, "Money currency cannot be null");
    try {
      amount = amount.setScale(currency.fractionDigits(), RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Money amount has unsupported precision for currency " + currency.value(), exception);
    }
  }

  public static Money of(String amount, CurrencyCode currency) {
    Objects.requireNonNull(amount, "Money amount text cannot be null");
    try {
      return new Money(new BigDecimal(amount), currency);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Money amount must be a valid decimal number", exception);
    }
  }

  public static Money zero(CurrencyCode currency) {
    Objects.requireNonNull(currency, "Money currency cannot be null");
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "Money operand cannot be null");
    if (!currency.equals(other.currency)) {
      throw new IllegalArgumentException("Money currencies must match");
    }
  }
}
