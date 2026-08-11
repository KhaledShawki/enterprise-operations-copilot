package io.github.khaledshawki.eoc.operations.application.model.event;

import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import java.math.BigDecimal;
import java.util.Objects;

public record OperationsMoneyPayload(BigDecimal amount, String currency) {

  public OperationsMoneyPayload {
    Objects.requireNonNull(amount, "Event money amount cannot be null");
    CurrencyCode currencyCode = CurrencyCode.of(currency);
    Money normalized = new Money(amount, currencyCode);
    amount = normalized.amount();
    currency = currencyCode.value();
  }

  public static OperationsMoneyPayload from(Money money) {
    Objects.requireNonNull(money, "Event money cannot be null");
    return new OperationsMoneyPayload(money.amount(), money.currency().value());
  }

  Money toMoney() {
    return new Money(amount, CurrencyCode.of(currency));
  }
}
