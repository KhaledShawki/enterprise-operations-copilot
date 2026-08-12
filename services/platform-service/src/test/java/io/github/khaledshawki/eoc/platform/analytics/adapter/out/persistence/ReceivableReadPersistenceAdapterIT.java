package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsIntegrationEvent;
import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsProjectionPayload;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "eoc.connector-outbox.relay-enabled=false",
      "eoc.operations-outbox.relay-enabled=false",
      "eoc.analytics-events.transport=disabled"
    })
@Import(TestcontainersConfiguration.class)
class ReceivableReadPersistenceAdapterIT {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000102");
  private static final UUID CUSTOMER_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID CUSTOMER_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID CUSTOMER_UNPROJECTED =
      UUID.fromString("00000000-0000-0000-0000-000000000203");
  private static final UUID INVOICE_OVERDUE =
      UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID INVOICE_FUTURE =
      UUID.fromString("00000000-0000-0000-0000-000000000302");
  private static final UUID INVOICE_PAID = UUID.fromString("00000000-0000-0000-0000-000000000303");
  private static final UUID INVOICE_UNPROJECTED_CUSTOMER =
      UUID.fromString("00000000-0000-0000-0000-000000000304");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T12:00:00Z");

  @Autowired private ConsumeAnalyticsIntegrationEventUseCase consumeUseCase;
  @Autowired private GetReceivableUseCase getReceivableUseCase;
  @Autowired private ListReceivablesUseCase listReceivablesUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM analytics_business_partner_projections");
    jdbcTemplate.update("DELETE FROM analytics_invoice_receivable_projections");
    jdbcTemplate.update("DELETE FROM analytics_inbox_events");

    consumeUseCase.consume(businessPartnerEvent(TENANT_A, CUSTOMER_A, "C-100", "Acme AG"));
    consumeUseCase.consume(businessPartnerEvent(TENANT_A, CUSTOMER_B, "C-200", "Beta AG"));
    consumeUseCase.consume(businessPartnerEvent(TENANT_B, CUSTOMER_A, "B-100", "Other AG"));

    consumeUseCase.consume(
        invoiceEvent(
            TENANT_A,
            INVOICE_OVERDUE,
            CUSTOMER_A,
            "INV-OVERDUE",
            "100.00",
            "25.00",
            LocalDate.of(2026, 8, 1),
            "PARTIALLY_PAID"));
    consumeUseCase.consume(
        invoiceEvent(
            TENANT_A,
            INVOICE_FUTURE,
            CUSTOMER_A,
            "INV-FUTURE",
            "200.00",
            "0.00",
            LocalDate.of(2026, 8, 20),
            "OPEN"));
    consumeUseCase.consume(
        invoiceEvent(
            TENANT_A,
            INVOICE_PAID,
            CUSTOMER_B,
            "INV-PAID",
            "50.00",
            "50.00",
            LocalDate.of(2026, 7, 20),
            "PAID"));
    consumeUseCase.consume(
        invoiceEvent(
            TENANT_A,
            INVOICE_UNPROJECTED_CUSTOMER,
            CUSTOMER_UNPROJECTED,
            "INV-NO-CUSTOMER",
            "10.00",
            "0.00",
            LocalDate.of(2026, 8, 5),
            "OPEN"));
    consumeUseCase.consume(
        invoiceEvent(
            TENANT_B,
            INVOICE_OVERDUE,
            CUSTOMER_A,
            "OTHER-TENANT-INVOICE",
            "999.00",
            "0.00",
            LocalDate.of(2026, 7, 1),
            "OPEN"));
  }

  @Test
  void getsTenantScopedReceivableAndJoinsCustomerProjectionWhenAvailable() {
    var tenantA =
        getReceivableUseCase.get(new GetReceivableQuery(TENANT_A, INVOICE_OVERDUE, BUSINESS_DATE));
    var tenantB =
        getReceivableUseCase.get(new GetReceivableQuery(TENANT_B, INVOICE_OVERDUE, BUSINESS_DATE));

    assertEquals("INV-OVERDUE", tenantA.invoiceNumber());
    assertEquals("Acme AG", tenantA.customer().displayName().orElseThrow());
    assertEquals(new BigDecimal("75.00"), tenantA.outstandingAmount().amount());
    assertTrue(tenantA.overdue());

    assertEquals("OTHER-TENANT-INVOICE", tenantB.invoiceNumber());
    assertEquals("Other AG", tenantB.customer().displayName().orElseThrow());
    assertEquals(new BigDecimal("999.00"), tenantB.outstandingAmount().amount());
  }

  @Test
  void preservesInvoiceWhenCustomerProjectionHasNotArrived() {
    var result =
        getReceivableUseCase.get(
            new GetReceivableQuery(TENANT_A, INVOICE_UNPROJECTED_CUSTOMER, BUSINESS_DATE));

    assertEquals(CUSTOMER_UNPROJECTED, result.customer().customerId());
    assertFalse(result.customer().projected());
    assertTrue(result.customer().displayName().isEmpty());
  }

  @Test
  void filtersOverdueStatusAndCustomerWithoutLeakingAnotherTenant() {
    var result =
        listReceivablesUseCase.list(
            new ListReceivablesQuery(
                TENANT_A,
                Optional.of(CUSTOMER_A),
                Set.of(InvoiceReceivableStatus.OPEN, InvoiceReceivableStatus.PARTIALLY_PAID),
                Optional.of(true),
                BUSINESS_DATE,
                0,
                20,
                ReceivableSortField.DUE_DATE,
                SortDirection.ASC));

    assertEquals(1, result.totalElements());
    assertEquals(1, result.receivables().size());
    assertEquals(INVOICE_OVERDUE, result.receivables().getFirst().invoiceId());
    assertTrue(result.receivables().getFirst().overdue());
  }

  @Test
  void supportsNotOverdueFilterAndDeterministicPagingByOutstandingAmount() {
    var notOverdue =
        listReceivablesUseCase.list(
            new ListReceivablesQuery(
                TENANT_A,
                Optional.of(CUSTOMER_A),
                Set.of(InvoiceReceivableStatus.OPEN, InvoiceReceivableStatus.PARTIALLY_PAID),
                Optional.of(false),
                BUSINESS_DATE,
                0,
                20,
                ReceivableSortField.DUE_DATE,
                SortDirection.ASC));

    assertEquals(1, notOverdue.totalElements());
    assertEquals(INVOICE_FUTURE, notOverdue.receivables().getFirst().invoiceId());
    assertFalse(notOverdue.receivables().getFirst().overdue());

    var firstPage =
        listReceivablesUseCase.list(
            new ListReceivablesQuery(
                TENANT_A,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                2,
                ReceivableSortField.OUTSTANDING_AMOUNT,
                SortDirection.DESC));
    var secondPage =
        listReceivablesUseCase.list(
            new ListReceivablesQuery(
                TENANT_A,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                1,
                2,
                ReceivableSortField.OUTSTANDING_AMOUNT,
                SortDirection.DESC));

    assertEquals(4, firstPage.totalElements());
    assertEquals(2, firstPage.totalPages());
    assertEquals(INVOICE_FUTURE, firstPage.receivables().get(0).invoiceId());
    assertEquals(INVOICE_OVERDUE, firstPage.receivables().get(1).invoiceId());
    assertTrue(firstPage.hasNext());
    assertEquals(2, secondPage.receivables().size());
    assertFalse(secondPage.hasNext());
    assertTrue(secondPage.hasPrevious());
  }

  private static AnalyticsIntegrationEvent businessPartnerEvent(
      UUID tenantId, UUID customerId, String number, String name) {
    UUID eventId = UUID.randomUUID();
    return new AnalyticsIntegrationEvent(
        eventId,
        "operations.business-partner.synchronized.v1",
        1,
        tenantId,
        "BUSINESS_PARTNER",
        customerId,
        1,
        "{}",
        OCCURRED_AT,
        new AnalyticsProjectionPayload.BusinessPartner(
            customerId, number, name, Set.of("CUSTOMER")));
  }

  private static AnalyticsIntegrationEvent invoiceEvent(
      UUID tenantId,
      UUID invoiceId,
      UUID customerId,
      String invoiceNumber,
      String originalAmount,
      String paidAmount,
      LocalDate dueDate,
      String status) {
    return new AnalyticsIntegrationEvent(
        UUID.randomUUID(),
        "operations.invoice.synchronized.v1",
        1,
        tenantId,
        "INVOICE",
        invoiceId,
        1,
        "{}",
        OCCURRED_AT.plusSeconds(1),
        new AnalyticsProjectionPayload.InvoiceReceivable(
            invoiceId,
            customerId,
            invoiceNumber,
            new BigDecimal(originalAmount),
            new BigDecimal(paidAmount),
            "EUR",
            LocalDate.of(2026, 7, 1),
            dueDate,
            false,
            status));
  }
}
