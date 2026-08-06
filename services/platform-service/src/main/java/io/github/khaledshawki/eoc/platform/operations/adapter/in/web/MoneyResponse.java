package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.math.BigDecimal;
import java.util.Objects;

public record MoneyResponse(BigDecimal amount, String currency) {

  public MoneyResponse {
    Objects.requireNonNull(amount, "Money response amount cannot be null");
    Objects.requireNonNull(currency, "Money response currency cannot be null");
  }

  static MoneyResponse from(Money money) {
    Objects.requireNonNull(money, "Money cannot be null");
    return new MoneyResponse(money.amount(), money.currency().value());
  }
}
