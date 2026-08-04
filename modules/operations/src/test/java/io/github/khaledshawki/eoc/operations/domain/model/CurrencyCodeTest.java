package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CurrencyCodeTest {

  @Test
  void shouldNormalizeValidIsoCurrencyCode() {
    CurrencyCode currency = CurrencyCode.of(" eur ");

    assertEquals("EUR", currency.value());
    assertEquals(2, currency.fractionDigits());
  }

  @Test
  void shouldExposeCurrencySpecificFractionDigits() {
    assertEquals(0, CurrencyCode.of("JPY").fractionDigits());
    assertEquals(3, CurrencyCode.of("KWD").fractionDigits());
  }

  @Test
  void shouldRejectMissingBlankAndUnknownCurrencyCodes() {
    assertThrows(NullPointerException.class, () -> CurrencyCode.of(null));
    assertThrows(IllegalArgumentException.class, () -> CurrencyCode.of(" "));
    assertThrows(IllegalArgumentException.class, () -> CurrencyCode.of("ZZZ"));
  }
}
