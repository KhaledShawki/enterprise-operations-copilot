package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  private static final CurrencyCode EUR = CurrencyCode.of("EUR");
  private static final CurrencyCode USD = CurrencyCode.of("USD");

  @Test
  void shouldCanonicalizeAmountToCurrencyScale() {
    assertEquals(new BigDecimal("10.00"), Money.of("10", EUR).amount());
    assertEquals(new BigDecimal("100"), Money.of("100", CurrencyCode.of("JPY")).amount());
  }

  @Test
  void shouldRejectMissingAmountsAndCurrencies() {
    assertThrows(NullPointerException.class, () -> new Money(null, EUR));
    assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ZERO, null));
    assertThrows(NullPointerException.class, () -> Money.of(null, EUR));
    assertThrows(NullPointerException.class, () -> Money.zero(null));
  }

  @Test
  void shouldRejectUnsupportedCurrencyPrecisionAndInvalidAmountText() {
    assertThrows(IllegalArgumentException.class, () -> Money.of("10.001", EUR));
    assertThrows(IllegalArgumentException.class, () -> Money.of("not-a-number", EUR));
  }

  @Test
  void shouldPerformDeterministicSameCurrencyArithmetic() {
    Money amount = Money.of("10.00", EUR);

    assertEquals(Money.of("12.50", EUR), amount.add(Money.of("2.50", EUR)));
    assertEquals(Money.of("7.50", EUR), amount.subtract(Money.of("2.50", EUR)));
    assertTrue(amount.compareTo(Money.of("9.99", EUR)) > 0);
  }

  @Test
  void shouldRejectCrossCurrencyArithmeticAndComparison() {
    Money euros = Money.of("10.00", EUR);
    Money dollars = Money.of("10.00", USD);

    assertThrows(IllegalArgumentException.class, () -> euros.add(dollars));
    assertThrows(IllegalArgumentException.class, () -> euros.subtract(dollars));
    assertThrows(IllegalArgumentException.class, () -> euros.compareTo(dollars));
    assertThrows(NullPointerException.class, () -> euros.add(null));
    assertThrows(NullPointerException.class, () -> euros.subtract(null));
    assertThrows(NullPointerException.class, () -> euros.compareTo(null));
  }

  @Test
  void shouldCreateCurrencyAwareZeroAndReportSign() {
    Money zero = Money.zero(EUR);

    assertEquals(Money.of("0.00", EUR), zero);
    assertTrue(zero.isZero());
    assertTrue(Money.of("1.00", EUR).isPositive());
    assertTrue(Money.of("-1.00", EUR).isNegative());
  }
}
