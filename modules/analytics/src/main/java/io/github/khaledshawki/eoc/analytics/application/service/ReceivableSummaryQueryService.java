package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSummarySnapshot;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablesSummaryResult;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableSummaryReadPort;
import java.util.HashSet;
import java.util.Objects;

public final class ReceivableSummaryQueryService implements GetReceivablesSummaryUseCase {

  private final ReceivableSummaryReadPort readPort;

  public ReceivableSummaryQueryService(ReceivableSummaryReadPort readPort) {
    this.readPort = Objects.requireNonNull(readPort, "Receivable summary read port cannot be null");
  }

  @Override
  public ReceivablesSummaryResult get(GetReceivablesSummaryQuery query) {
    Objects.requireNonNull(query, "Get receivables summary query cannot be null");
    ReceivableSummarySnapshot snapshot =
        Objects.requireNonNull(
            readPort.summarize(query.analyticsTenantId(), query.businessDate()),
            "Receivable summary snapshot cannot be null");
    if (!snapshot.tenantId().equals(query.analyticsTenantId())
        || !snapshot.businessDate().equals(query.businessDate())) {
      throw corrupted("read adapter returned a summary for a different tenant or business date");
    }
    requireDeterministicCurrencies(snapshot);
    try {
      return ReceivablesSummaryResult.from(snapshot);
    } catch (ArithmeticException | IllegalArgumentException exception) {
      AnalyticsProjectionStateCorruptedException corrupted =
          corrupted("receivable summary totals are invalid");
      corrupted.initCause(exception);
      throw corrupted;
    }
  }

  private static void requireDeterministicCurrencies(ReceivableSummarySnapshot snapshot) {
    HashSet<String> currencies = new HashSet<>();
    String previous = null;
    for (ReceivableCurrencySummary summary : snapshot.currencies()) {
      String current = summary.currency().value();
      if (!currencies.add(current) || (previous != null && previous.compareTo(current) >= 0)) {
        throw corrupted(
            "read adapter returned duplicate or non-deterministically ordered currencies");
      }
      previous = current;
    }
  }

  private static AnalyticsProjectionStateCorruptedException corrupted(String detail) {
    return new AnalyticsProjectionStateCorruptedException(detail);
  }
}
