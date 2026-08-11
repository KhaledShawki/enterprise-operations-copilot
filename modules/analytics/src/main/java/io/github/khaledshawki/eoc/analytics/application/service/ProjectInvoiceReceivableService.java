package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionConcurrentModificationException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.out.InvoiceReceivableProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.util.Objects;
import java.util.Optional;

public final class ProjectInvoiceReceivableService implements ProjectInvoiceReceivableUseCase {

  private static final String AGGREGATE_TYPE = "INVOICE";

  private final InvoiceReceivableProjectionRepository repository;

  public ProjectInvoiceReceivableService(InvoiceReceivableProjectionRepository repository) {
    this.repository =
        Objects.requireNonNull(
            repository, "Invoice receivable projection repository cannot be null");
  }

  @Override
  public ProjectionApplyResult project(ProjectInvoiceReceivableCommand command) {
    Objects.requireNonNull(command, "Invoice receivable projection command cannot be null");
    AnalyticsTenantId tenantId = AnalyticsTenantId.of(command.tenantId());
    ProjectionCursor source =
        new ProjectionCursor(command.eventId(), command.aggregateVersion(), command.occurredAt());
    CurrencyCode currency = CurrencyCode.of(command.currency());
    InvoiceReceivableProjection incoming =
        new InvoiceReceivableProjection(
            tenantId,
            command.invoiceId(),
            command.customerId(),
            command.invoiceNumber(),
            new AnalyticsMoney(command.originalAmount(), currency),
            new AnalyticsMoney(command.paidAmount(), currency),
            command.issueDate(),
            command.dueDate(),
            command.cancelled(),
            InvoiceReceivableStatus.fromContractCode(command.status()),
            source);

    Optional<InvoiceReceivableProjection> current =
        repository.findById(tenantId, command.invoiceId());
    if (current.isEmpty()) {
      ProjectionSequence.requireInitial(source, AGGREGATE_TYPE, command.invoiceId());
      persist(incoming, 0);
      return ProjectionApplyResult.applied(source.eventId(), source.aggregateVersion());
    }

    InvoiceReceivableProjection existing = current.orElseThrow();
    requireRequestedIdentity(existing, tenantId, command);
    ProjectionSequence.SequenceDecision decision =
        ProjectionSequence.evaluate(existing.source(), source, AGGREGATE_TYPE, command.invoiceId());
    if (decision == ProjectionSequence.SequenceDecision.SAME_VERSION) {
      if (existing.equals(incoming)) {
        return ProjectionApplyResult.duplicate(source.eventId(), source.aggregateVersion());
      }
      throw new AnalyticsProjectionVersionConflictException(
          AGGREGATE_TYPE,
          command.invoiceId(),
          source.aggregateVersion(),
          "the same event identity carries different invoice projection facts");
    }

    persist(incoming, existing.source().aggregateVersion());
    return ProjectionApplyResult.applied(source.eventId(), source.aggregateVersion());
  }

  private static void requireRequestedIdentity(
      InvoiceReceivableProjection projection,
      AnalyticsTenantId tenantId,
      ProjectInvoiceReceivableCommand command) {
    if (!projection.tenantId().equals(tenantId)
        || !projection.invoiceId().equals(command.invoiceId())) {
      throw new AnalyticsProjectionStateCorruptedException(
          "invoice receivable repository returned a projection for another tenant or aggregate");
    }
  }

  private void persist(InvoiceReceivableProjection projection, long expectedCurrentVersion) {
    if (!repository.saveIfCurrentVersion(projection, expectedCurrentVersion)) {
      throw new AnalyticsProjectionConcurrentModificationException(
          AGGREGATE_TYPE, projection.invoiceId(), expectedCurrentVersion);
    }
  }
}
