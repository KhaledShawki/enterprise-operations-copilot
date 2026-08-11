package io.github.khaledshawki.eoc.analytics.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AnalyticsMoneyTest {

  @Test
  void canonicalizesCurrencyAndScale() {
    AnalyticsMoney money = AnalyticsMoney.of(new BigDecimal("12.3"), " eur ");

    assertEquals(new BigDecimal("12.30"), money.amount());
    assertEquals("EUR", money.currency().value());
  }

  @Test
  void rejectsUnsupportedCurrencyPrecision() {
    assertThrows(
        IllegalArgumentException.class, () -> AnalyticsMoney.of(new BigDecimal("12.345"), "EUR"));
  }

  @Test
  void subtractionRequiresSameCurrency() {
    AnalyticsMoney eur = AnalyticsMoney.of(new BigDecimal("10.00"), "EUR");
    AnalyticsMoney usd = AnalyticsMoney.of(new BigDecimal("1.00"), "USD");

    assertThrows(IllegalArgumentException.class, () -> eur.subtract(usd));
  }
}
