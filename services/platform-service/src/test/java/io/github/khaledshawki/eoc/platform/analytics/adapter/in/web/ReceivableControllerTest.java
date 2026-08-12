package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsReadUnavailableException;
import io.github.khaledshawki.eoc.analytics.application.exception.ReceivableNotFoundException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCustomerSummary;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableSortField;
import io.github.khaledshawki.eoc.analytics.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablePageResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivableResult;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableStatus;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReceivableController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceivableControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("receivable-controller-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject("receivable-reader")
              .build());
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/analytics/receivables";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetReceivableUseCase getReceivableUseCase;
  @MockitoBean private ListReceivablesUseCase listReceivablesUseCase;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2026-08-12T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @Test
  void protectsEveryPublicReceivableEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(ReceivableController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void getsReceivableWithCustomerAndSourceEvidence() throws Exception {
    when(getReceivableUseCase.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE)))
        .thenReturn(result());

    mockMvc
        .perform(
            get(ENDPOINT + "/" + INVOICE_ID)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(INVOICE_ID.toString()))
        .andExpect(jsonPath("$.customer.id").value(CUSTOMER_ID.toString()))
        .andExpect(jsonPath("$.customer.projected").value(true))
        .andExpect(jsonPath("$.customer.displayName").value("Acme AG"))
        .andExpect(jsonPath("$.outstandingAmount.amount").value(75.00))
        .andExpect(jsonPath("$.outstandingAmount.currency").value("EUR"))
        .andExpect(jsonPath("$.overdue").value(true))
        .andExpect(jsonPath("$.source.aggregateVersion").value(2));

    verify(getReceivableUseCase).get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE));
  }

  @Test
  void listsReceivablesWithFiltersPagingAndSorting() throws Exception {
    ListReceivablesQuery query =
        new ListReceivablesQuery(
            TENANT_ID,
            Optional.of(CUSTOMER_ID),
            Set.of(InvoiceReceivableStatus.OPEN, InvoiceReceivableStatus.PARTIALLY_PAID),
            Optional.of(true),
            BUSINESS_DATE,
            1,
            25,
            ReceivableSortField.OUTSTANDING_AMOUNT,
            SortDirection.DESC);
    when(listReceivablesUseCase.list(query))
        .thenReturn(
            new ReceivablePageResult(List.of(result()), 1, 25, 26, 2, BUSINESS_DATE, false, true));

    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("customerId", CUSTOMER_ID.toString())
                .param("status", "OPEN", "PARTIALLY_PAID")
                .param("overdue", "true")
                .param("businessDate", BUSINESS_DATE.toString())
                .param("page", "1")
                .param("size", "25")
                .param("sort", "OUTSTANDING_AMOUNT")
                .param("direction", "DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receivables.length()").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(26))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
        .andExpect(jsonPath("$.hasPrevious").value(true));

    verify(listReceivablesUseCase).list(query);
  }

  @Test
  void defaultsBusinessDateFromInjectedClock() throws Exception {
    ListReceivablesQuery query =
        new ListReceivablesQuery(
            TENANT_ID,
            Optional.empty(),
            Set.of(),
            Optional.empty(),
            BUSINESS_DATE,
            0,
            50,
            ReceivableSortField.DUE_DATE,
            SortDirection.ASC);
    when(listReceivablesUseCase.list(query))
        .thenReturn(new ReceivablePageResult(List.of(), 0, 50, 0, 0, BUSINESS_DATE, false, false));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()));

    verify(listReceivablesUseCase).list(query);
  }

  @Test
  void returnsStableProblemsForNotFoundInvalidQueryAndReadOutage() throws Exception {
    when(getReceivableUseCase.get(new GetReceivableQuery(TENANT_ID, INVOICE_ID, BUSINESS_DATE)))
        .thenThrow(new ReceivableNotFoundException(AnalyticsTenantId.of(TENANT_ID), INVOICE_ID));

    mockMvc
        .perform(
            get(ENDPOINT + "/" + INVOICE_ID)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("RECEIVABLE_NOT_FOUND"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_QUERY"));

    when(listReceivablesUseCase.list(org.mockito.ArgumentMatchers.any()))
        .thenThrow(
            new AnalyticsReadUnavailableException(new RuntimeException("database unavailable")));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ANALYTICS_READ_UNAVAILABLE"));
  }

  @Test
  void rejectsInvalidEnumAsAStableQueryProblem() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).param("status", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_QUERY"));
  }

  private static ReceivableResult result() {
    CurrencyCode eur = CurrencyCode.of("EUR");
    return new ReceivableResult(
        TENANT_ID,
        INVOICE_ID,
        new ReceivableCustomerSummary(CUSTOMER_ID, Optional.of("C-100"), Optional.of("Acme AG")),
        "INV-100",
        new AnalyticsMoney(new BigDecimal("100.00"), eur),
        new AnalyticsMoney(new BigDecimal("25.00"), eur),
        new AnalyticsMoney(new BigDecimal("75.00"), eur),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        BUSINESS_DATE,
        InvoiceReceivableStatus.PARTIALLY_PAID,
        false,
        true,
        new ProjectionCursor(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            2,
            Instant.parse("2026-08-02T12:00:00Z")));
  }
}
