package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionStatus;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsInboxAcceptance;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.out.AnalyticsIntegrationEventInbox;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsumeAnalyticsIntegrationEventServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-12T00:00:00Z");

  @Test
  void appliesBusinessPartnerAfterNewInboxAcceptance() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.ACCEPTED);
    CapturingBusinessPartnerProjector partnerProjector = new CapturingBusinessPartnerProjector();
    CapturingInvoiceProjector invoiceProjector = new CapturingInvoiceProjector();
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(inbox, partnerProjector, invoiceProjector);
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), UUID.randomUUID(), 1);

    var result = service.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.APPLIED, result.status());
    assertEquals(event.eventId(), result.eventId());
    assertEquals(1, result.aggregateVersion());
    assertEquals(event.aggregateId(), partnerProjector.command.businessPartnerId());
    assertEquals(0, invoiceProjector.calls);
  }

  @Test
  void appliesInvoiceAfterNewInboxAcceptance() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.ACCEPTED);
    CapturingBusinessPartnerProjector partnerProjector = new CapturingBusinessPartnerProjector();
    CapturingInvoiceProjector invoiceProjector = new CapturingInvoiceProjector();
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(inbox, partnerProjector, invoiceProjector);
    AnalyticsIntegrationEvent event = invoiceEvent(UUID.randomUUID(), UUID.randomUUID(), 1);

    var result = service.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.APPLIED, result.status());
    assertEquals(event.aggregateId(), invoiceProjector.command.invoiceId());
    assertEquals(0, partnerProjector.calls);
  }

  @Test
  void absorbsHistoricalDuplicateBeforeProjection() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.DUPLICATE);
    CapturingBusinessPartnerProjector partnerProjector = new CapturingBusinessPartnerProjector();
    CapturingInvoiceProjector invoiceProjector = new CapturingInvoiceProjector();
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(inbox, partnerProjector, invoiceProjector);
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), UUID.randomUUID(), 4);

    var result = service.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.DUPLICATE, result.status());
    assertEquals(0, partnerProjector.calls);
    assertEquals(0, invoiceProjector.calls);
  }

  @Test
  void durablyAcceptsKnownEventThatHasNoProjectionYet() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.ACCEPTED);
    CapturingBusinessPartnerProjector partnerProjector = new CapturingBusinessPartnerProjector();
    CapturingInvoiceProjector invoiceProjector = new CapturingInvoiceProjector();
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(inbox, partnerProjector, invoiceProjector);
    UUID paymentId = UUID.randomUUID();
    AnalyticsIntegrationEvent event =
        new AnalyticsIntegrationEvent(
            UUID.randomUUID(),
            "operations.payment.synchronized.v1",
            1,
            TENANT_ID,
            "PAYMENT",
            paymentId,
            3,
            "{}",
            OCCURRED_AT,
            new AnalyticsProjectionPayload.Ignored());

    var result = service.consume(event);

    assertEquals(AnalyticsEventConsumptionStatus.IGNORED, result.status());
    assertEquals(0, partnerProjector.calls);
    assertEquals(0, invoiceProjector.calls);
  }

  @Test
  void rejectsTypedPayloadThatDoesNotMatchEnvelopeAggregate() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.ACCEPTED);
    CapturingBusinessPartnerProjector partnerProjector = new CapturingBusinessPartnerProjector();
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(
            inbox, partnerProjector, new CapturingInvoiceProjector());
    AnalyticsIntegrationEvent valid = businessPartnerEvent(UUID.randomUUID(), UUID.randomUUID(), 1);
    AnalyticsIntegrationEvent corrupted =
        new AnalyticsIntegrationEvent(
            valid.eventId(),
            valid.eventType(),
            valid.schemaVersion(),
            valid.tenantId(),
            valid.aggregateType(),
            UUID.randomUUID(),
            valid.aggregateVersion(),
            valid.payload(),
            valid.occurredAt(),
            valid.projectionPayload());

    AnalyticsEventConsumptionException exception =
        assertThrows(AnalyticsEventConsumptionException.class, () -> service.consume(corrupted));

    assertEquals("analytics-event-contract-invalid", exception.failureCode());
    assertEquals(false, exception.retryable());
    assertEquals(0, partnerProjector.calls);
  }

  @Test
  void treatsProjectionDuplicateAfterNewInboxAcceptanceAsCorruption() {
    StubInbox inbox = new StubInbox(AnalyticsInboxAcceptance.ACCEPTED);
    ProjectBusinessPartnerUseCase duplicateProjector =
        command -> ProjectionApplyResult.duplicate(command.eventId(), command.aggregateVersion());
    ConsumeAnalyticsIntegrationEventService service =
        new ConsumeAnalyticsIntegrationEventService(
            inbox, duplicateProjector, new CapturingInvoiceProjector());
    AnalyticsIntegrationEvent event = businessPartnerEvent(UUID.randomUUID(), UUID.randomUUID(), 1);

    AnalyticsEventConsumptionException exception =
        assertThrows(AnalyticsEventConsumptionException.class, () -> service.consume(event));

    assertEquals("analytics-inbox-projection-state-corrupted", exception.failureCode());
    assertInstanceOf(AnalyticsProjectionPayload.BusinessPartner.class, event.projectionPayload());
  }

  private static AnalyticsIntegrationEvent businessPartnerEvent(
      UUID eventId, UUID partnerId, long version) {
    return new AnalyticsIntegrationEvent(
        eventId,
        "operations.business-partner.synchronized.v1",
        1,
        TENANT_ID,
        "BUSINESS_PARTNER",
        partnerId,
        version,
        """
        {"businessPartnerId":"%s"}
        """
            .formatted(partnerId),
        OCCURRED_AT,
        new AnalyticsProjectionPayload.BusinessPartner(
            partnerId, "C-100", "Acme AG", Set.of("CUSTOMER")));
  }

  private static AnalyticsIntegrationEvent invoiceEvent(
      UUID eventId, UUID invoiceId, long version) {
    return new AnalyticsIntegrationEvent(
        eventId,
        "operations.invoice.synchronized.v1",
        1,
        TENANT_ID,
        "INVOICE",
        invoiceId,
        version,
        """
        {"invoiceId":"%s"}
        """
            .formatted(invoiceId),
        OCCURRED_AT,
        new AnalyticsProjectionPayload.InvoiceReceivable(
            invoiceId,
            UUID.randomUUID(),
            "INV-100",
            new BigDecimal("100.00"),
            new BigDecimal("20.00"),
            "CHF",
            LocalDate.parse("2026-07-01"),
            LocalDate.parse("2026-07-31"),
            false,
            "PARTIALLY_PAID"));
  }

  private static final class StubInbox implements AnalyticsIntegrationEventInbox {

    private final AnalyticsInboxAcceptance acceptance;

    private StubInbox(AnalyticsInboxAcceptance acceptance) {
      this.acceptance = acceptance;
    }

    @Override
    public AnalyticsInboxAcceptance accept(AnalyticsIntegrationEvent event) {
      return acceptance;
    }
  }

  private static final class CapturingBusinessPartnerProjector
      implements ProjectBusinessPartnerUseCase {

    private int calls;
    private ProjectBusinessPartnerCommand command;

    @Override
    public ProjectionApplyResult project(ProjectBusinessPartnerCommand command) {
      calls++;
      this.command = command;
      return ProjectionApplyResult.applied(command.eventId(), command.aggregateVersion());
    }
  }

  private static final class CapturingInvoiceProjector implements ProjectInvoiceReceivableUseCase {

    private int calls;
    private ProjectInvoiceReceivableCommand command;

    @Override
    public ProjectionApplyResult project(ProjectInvoiceReceivableCommand command) {
      calls++;
      this.command = command;
      return ProjectionApplyResult.applied(command.eventId(), command.aggregateVersion());
    }
  }
}
