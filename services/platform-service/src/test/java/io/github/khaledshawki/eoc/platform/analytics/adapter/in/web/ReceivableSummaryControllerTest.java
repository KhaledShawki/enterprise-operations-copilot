package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsReadUnavailableException;
import io.github.khaledshawki.eoc.analytics.application.model.querying.ReceivableCurrencySummary;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryQuery;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ReceivablesSummaryResult;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsMoney;
import io.github.khaledshawki.eoc.analytics.domain.model.CurrencyCode;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReceivableSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceivableSummaryControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("receivable-summary-controller-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject("receivable-summary-reader")
              .build());
  private static final String ENDPOINT =
      "/api/v1/tenants/" + TENANT_ID + "/analytics/receivables/summary";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetReceivablesSummaryUseCase getReceivablesSummaryUseCase;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2026-08-12T10:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @Test
  void protectsEveryPublicSummaryEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(ReceivableSummaryController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void returnsCurrencySeparatedOperationalSummary() throws Exception {
    var query = new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE);
    when(getReceivablesSummaryUseCase.get(query)).thenReturn(result());

    mockMvc
        .perform(
            get(ENDPOINT).principal(AUTHENTICATION).param("businessDate", BUSINESS_DATE.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
        .andExpect(jsonPath("$.invoiceCount").value(9))
        .andExpect(jsonPath("$.openCount").value(7))
        .andExpect(jsonPath("$.overdueCount").value(5))
        .andExpect(jsonPath("$.currencies[0].currency").value("CHF"))
        .andExpect(jsonPath("$.currencies[0].outstandingAmount").value(500.00))
        .andExpect(jsonPath("$.currencies[1].currency").value("EUR"))
        .andExpect(jsonPath("$.currencies[1].overdueAmount").value(300.00))
        .andExpect(jsonPath("$.currencies[1].aging.days91PlusOverdueAmount").value(60.00));

    verify(getReceivablesSummaryUseCase).get(query);
  }

  @Test
  void defaultsBusinessDateFromInjectedClock() throws Exception {
    var query = new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE);
    when(getReceivablesSummaryUseCase.get(query))
        .thenReturn(new ReceivablesSummaryResult(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of()));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()));

    verify(getReceivablesSummaryUseCase).get(query);
  }

  @Test
  void returnsStableProblemForReadOutageAndInvalidDate() throws Exception {
    when(getReceivablesSummaryUseCase.get(new GetReceivablesSummaryQuery(TENANT_ID, BUSINESS_DATE)))
        .thenThrow(
            new AnalyticsReadUnavailableException(new RuntimeException("database unavailable")));

    mockMvc
        .perform(
            get(ENDPOINT).principal(AUTHENTICATION).param("businessDate", BUSINESS_DATE.toString()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ANALYTICS_READ_UNAVAILABLE"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).param("businessDate", "not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_QUERY"));
  }

  private static ReceivablesSummaryResult result() {
    return new ReceivablesSummaryResult(
        TENANT_ID,
        BUSINESS_DATE,
        9,
        7,
        5,
        List.of(
            summary("CHF", 2, 2, 1, "500.00", "200.00", "300.00", "200.00", "0.00", "0.00", "0.00"),
            summary(
                "EUR", 7, 5, 4, "400.00", "300.00", "100.00", "90.00", "80.00", "70.00", "60.00")));
  }

  private static ReceivableCurrencySummary summary(
      String currency,
      long invoiceCount,
      long openCount,
      long overdueCount,
      String outstanding,
      String overdue,
      String current,
      String days1To30,
      String days31To60,
      String days61To90,
      String days91Plus) {
    CurrencyCode code = CurrencyCode.of(currency);
    return new ReceivableCurrencySummary(
        code,
        invoiceCount,
        openCount,
        overdueCount,
        money(outstanding, code),
        money(overdue, code),
        money(current, code),
        money(days1To30, code),
        money(days31To60, code),
        money(days61To90, code),
        money(days91Plus, code));
  }

  private static AnalyticsMoney money(String amount, CurrencyCode currency) {
    return new AnalyticsMoney(new BigDecimal(amount), currency);
  }
}
