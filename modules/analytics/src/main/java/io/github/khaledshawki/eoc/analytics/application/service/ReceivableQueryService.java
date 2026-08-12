package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.ReceivableNotFoundException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivablePage;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableQueryCriteria;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSnapshot;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablePageResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivableResult;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableReadPort;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

public final class ReceivableQueryService implements GetReceivableUseCase, ListReceivablesUseCase {

  private final ReceivableReadPort readPort;

  public ReceivableQueryService(ReceivableReadPort readPort) {
    this.readPort = Objects.requireNonNull(readPort, "Receivable read port cannot be null");
  }

  @Override
  public ReceivableResult get(GetReceivableQuery query) {
    Objects.requireNonNull(query, "Get receivable query cannot be null");
    AnalyticsTenantId tenantId = query.analyticsTenantId();
    ReceivableSnapshot snapshot =
        readPort
            .findById(tenantId, query.invoiceId())
            .orElseThrow(() -> new ReceivableNotFoundException(tenantId, query.invoiceId()));
    requireIdentity(snapshot, tenantId, query.invoiceId());
    return ReceivableResult.from(snapshot, query.businessDate());
  }

  @Override
  public ReceivablePageResult list(ListReceivablesQuery query) {
    Objects.requireNonNull(query, "List receivables query cannot be null");
    ReceivableQueryCriteria criteria = query.criteria();
    ReceivablePage page =
        Objects.requireNonNull(readPort.findPage(criteria), "Receivable page cannot be null");
    if (page.pageNumber() != criteria.pageNumber() || page.pageSize() != criteria.pageSize()) {
      throw corrupted("read adapter returned page metadata for a different request");
    }

    HashSet<UUID> invoiceIds = new HashSet<>();
    for (ReceivableSnapshot snapshot : page.receivables()) {
      requireMatchesCriteria(snapshot, criteria);
      if (!invoiceIds.add(snapshot.invoice().invoiceId())) {
        throw corrupted("read adapter returned the same invoice more than once");
      }
    }
    return ReceivablePageResult.from(page, criteria.businessDate());
  }

  private static void requireIdentity(
      ReceivableSnapshot snapshot, AnalyticsTenantId tenantId, UUID invoiceId) {
    Objects.requireNonNull(snapshot, "Receivable snapshot cannot be null");
    if (!snapshot.invoice().tenantId().equals(tenantId)
        || !snapshot.invoice().invoiceId().equals(invoiceId)) {
      throw corrupted("read adapter returned a projection for a different tenant or invoice");
    }
  }

  private static void requireMatchesCriteria(
      ReceivableSnapshot snapshot, ReceivableQueryCriteria criteria) {
    Objects.requireNonNull(snapshot, "Receivable snapshot cannot be null");
    var invoice = snapshot.invoice();
    if (!invoice.tenantId().equals(criteria.tenantId())) {
      throw corrupted("read adapter returned a projection for a different tenant");
    }
    if (criteria.customerId().isPresent()
        && !criteria.customerId().orElseThrow().equals(invoice.customerId())) {
      throw corrupted("read adapter returned a projection for a different customer");
    }
    if (!criteria.statuses().isEmpty() && !criteria.statuses().contains(invoice.status())) {
      throw corrupted("read adapter returned a projection outside the requested statuses");
    }
    if (criteria.overdue().isPresent()
        && criteria.overdue().orElseThrow() != invoice.isOverdueOn(criteria.businessDate())) {
      throw corrupted("read adapter returned a projection outside the overdue filter");
    }
  }

  private static AnalyticsProjectionStateCorruptedException corrupted(String detail) {
    return new AnalyticsProjectionStateCorruptedException(detail);
  }
}
