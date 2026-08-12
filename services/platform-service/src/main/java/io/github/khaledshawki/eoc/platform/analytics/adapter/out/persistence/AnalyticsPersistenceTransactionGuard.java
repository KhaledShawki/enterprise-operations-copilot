package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class AnalyticsPersistenceTransactionGuard {

  private static final String TRANSACTION_REQUIRED = "analytics-consumption-transaction-required";

  private AnalyticsPersistenceTransactionGuard() {}

  static void requireActive() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new AnalyticsEventConsumptionException(TRANSACTION_REQUIRED, false, null);
    }
  }
}
