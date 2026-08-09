package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.math.BigDecimal;
import java.util.Objects;

public record MoneyRequest(BigDecimal amount, String currency) {

  public MoneyRequest {
    Objects.requireNonNull(amount, "Money request amount cannot be null");
    Objects.requireNonNull(currency, "Money request currency cannot be null");
  }

  Money toMoney() {
    return new Money(amount, CurrencyCode.of(currency));
  }
}
