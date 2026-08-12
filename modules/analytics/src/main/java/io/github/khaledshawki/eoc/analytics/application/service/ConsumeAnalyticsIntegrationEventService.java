package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyStatus;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionResult;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsInboxAcceptance;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.out.AnalyticsIntegrationEventInbox;
import java.util.Objects;

public final class ConsumeAnalyticsIntegrationEventService
    implements ConsumeAnalyticsIntegrationEventUseCase {

  private static final String CONTRACT_INVALID = "analytics-event-contract-invalid";
  private static final String STATE_CORRUPTED = "analytics-inbox-projection-state-corrupted";

  private final AnalyticsIntegrationEventInbox inbox;
  private final ProjectBusinessPartnerUseCase businessPartnerProjector;
  private final ProjectInvoiceReceivableUseCase invoiceProjector;

  public ConsumeAnalyticsIntegrationEventService(
      AnalyticsIntegrationEventInbox inbox,
      ProjectBusinessPartnerUseCase businessPartnerProjector,
      ProjectInvoiceReceivableUseCase invoiceProjector) {
    this.inbox = Objects.requireNonNull(inbox, "Analytics integration event inbox cannot be null");
    this.businessPartnerProjector =
        Objects.requireNonNull(
            businessPartnerProjector, "Business partner projector cannot be null");
    this.invoiceProjector =
        Objects.requireNonNull(invoiceProjector, "Invoice receivable projector cannot be null");
  }

  @Override
  public AnalyticsEventConsumptionResult consume(AnalyticsIntegrationEvent event) {
    Objects.requireNonNull(event, "Analytics integration event cannot be null");

    AnalyticsInboxAcceptance acceptance = inbox.accept(event);
    if (acceptance == AnalyticsInboxAcceptance.DUPLICATE) {
      return AnalyticsEventConsumptionResult.duplicate(event.eventId(), event.aggregateVersion());
    }

    return switch (event.projectionPayload()) {
      case AnalyticsProjectionPayload.BusinessPartner payload ->
          applyBusinessPartner(event, payload);
      case AnalyticsProjectionPayload.InvoiceReceivable payload -> applyInvoice(event, payload);
      case AnalyticsProjectionPayload.Ignored ignored ->
          AnalyticsEventConsumptionResult.ignored(event.eventId(), event.aggregateVersion());
    };
  }

  private AnalyticsEventConsumptionResult applyBusinessPartner(
      AnalyticsIntegrationEvent event, AnalyticsProjectionPayload.BusinessPartner payload) {
    requireAggregate(event, "BUSINESS_PARTNER", payload.businessPartnerId());
    ProjectionApplyResult result =
        businessPartnerProjector.project(
            new ProjectBusinessPartnerCommand(
                event.eventId(),
                event.tenantId(),
                payload.businessPartnerId(),
                event.aggregateVersion(),
                event.occurredAt(),
                payload.partnerNumber(),
                payload.displayName(),
                payload.roles()));
    return requireApplied(event, result);
  }

  private AnalyticsEventConsumptionResult applyInvoice(
      AnalyticsIntegrationEvent event, AnalyticsProjectionPayload.InvoiceReceivable payload) {
    requireAggregate(event, "INVOICE", payload.invoiceId());
    ProjectionApplyResult result =
        invoiceProjector.project(
            new ProjectInvoiceReceivableCommand(
                event.eventId(),
                event.tenantId(),
                payload.invoiceId(),
                event.aggregateVersion(),
                event.occurredAt(),
                payload.customerId(),
                payload.invoiceNumber(),
                payload.originalAmount(),
                payload.paidAmount(),
                payload.currency(),
                payload.issueDate(),
                payload.dueDate(),
                payload.cancelled(),
                payload.status()));
    return requireApplied(event, result);
  }

  private static void requireAggregate(
      AnalyticsIntegrationEvent event, String aggregateType, java.util.UUID aggregateId) {
    if (!event.aggregateType().equals(aggregateType) || !event.aggregateId().equals(aggregateId)) {
      throw new AnalyticsEventConsumptionException(CONTRACT_INVALID, false, null);
    }
  }

  private static AnalyticsEventConsumptionResult requireApplied(
      AnalyticsIntegrationEvent event, ProjectionApplyResult result) {
    if (result == null
        || result.status() != ProjectionApplyStatus.APPLIED
        || !result.eventId().equals(event.eventId())
        || result.aggregateVersion() != event.aggregateVersion()) {
      throw new AnalyticsEventConsumptionException(STATE_CORRUPTED, false, null);
    }
    return AnalyticsEventConsumptionResult.applied(event.eventId(), event.aggregateVersion());
  }
}
