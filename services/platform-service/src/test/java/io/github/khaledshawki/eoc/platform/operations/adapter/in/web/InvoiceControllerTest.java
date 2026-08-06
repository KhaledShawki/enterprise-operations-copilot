package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.operations.application.exception.InvalidInvoiceQueryException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceDueState;
import io.github.khaledshawki.eoc.operations.application.model.querying.InvoiceSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoicePageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.InvoiceResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceNumber;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceStatus;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.lang.reflect.Modifier;
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

@WebMvcTest(controllers = InvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final String SUBJECT = "invoice-reader";
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 6);
  private static final OperationsActor ACTOR = new OperationsActor(ISSUER.toString(), SUBJECT);
  private static final AuthenticatedUser AUTHENTICATED_USER =
      new AuthenticatedUser(ISSUER, SUBJECT, Set.of("auditor"));
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("invoice-controller-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject(SUBJECT)
              .build());
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/invoices";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetInvoiceUseCase getInvoiceUseCase;
  @MockitoBean private ListInvoicesUseCase listInvoicesUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(jwtAuthenticatedUserMapper.map(AUTHENTICATION)).thenReturn(AUTHENTICATED_USER);
    when(clock.instant()).thenReturn(Instant.parse("2026-08-06T12:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @Test
  void shouldProtectEveryPublicInvoiceEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(InvoiceController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void shouldGetInvoiceUsingExplicitBusinessDate() throws Exception {
    InvoiceResult result = result();
    when(getInvoiceUseCase.get(new GetInvoiceQuery(ACTOR, TENANT_ID, INVOICE_ID, BUSINESS_DATE)))
        .thenReturn(result);

    mockMvc
        .perform(
            get(ENDPOINT + "/" + INVOICE_ID)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(INVOICE_ID.toString()))
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.dueState").value("OVERDUE"))
        .andExpect(jsonPath("$.openAmount.amount").value(75.00))
        .andExpect(jsonPath("$.openAmount.currency").value("EUR"));

    verify(getInvoiceUseCase).get(new GetInvoiceQuery(ACTOR, TENANT_ID, INVOICE_ID, BUSINESS_DATE));
  }

  @Test
  void shouldListInvoicesWithFiltersAndPaging() throws Exception {
    ListInvoicesQuery query =
        new ListInvoicesQuery(
            ACTOR,
            TENANT_ID,
            Optional.of(CUSTOMER_ID),
            Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID),
            Optional.of(InvoiceDueState.OVERDUE),
            BUSINESS_DATE,
            1,
            25,
            InvoiceSortField.DUE_DATE,
            SortDirection.ASC);
    when(listInvoicesUseCase.list(query))
        .thenReturn(
            new InvoicePageResult(List.of(result()), 1, 25, 26, 2, BUSINESS_DATE, false, true));

    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("customerId", CUSTOMER_ID.toString())
                .param("status", "OPEN", "PARTIALLY_PAID")
                .param("dueState", "OVERDUE")
                .param("businessDate", BUSINESS_DATE.toString())
                .param("page", "1")
                .param("size", "25")
                .param("sort", "DUE_DATE")
                .param("direction", "ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoices.length()").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(26))
        .andExpect(jsonPath("$.businessDate").value(BUSINESS_DATE.toString()))
        .andExpect(jsonPath("$.hasPrevious").value(true));

    verify(listInvoicesUseCase).list(query);
  }

  @Test
  void shouldDefaultBusinessDateFromInjectedClock() throws Exception {
    ListInvoicesQuery query =
        new ListInvoicesQuery(
            ACTOR,
            TENANT_ID,
            Optional.empty(),
            Set.of(),
            Optional.empty(),
            BUSINESS_DATE,
            0,
            50,
            InvoiceSortField.ISSUE_DATE,
            SortDirection.DESC);
    when(listInvoicesUseCase.list(query))
        .thenReturn(new InvoicePageResult(List.of(), 0, 50, 0, 0, BUSINESS_DATE, false, false));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoices.length()").value(0));

    verify(listInvoicesUseCase).list(query);
  }

  @Test
  void shouldReturnInvoiceProblemDetails() throws Exception {
    when(getInvoiceUseCase.get(new GetInvoiceQuery(ACTOR, TENANT_ID, INVOICE_ID, BUSINESS_DATE)))
        .thenThrow(
            new InvoiceNotFoundException(
                OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID)));
    when(listInvoicesUseCase.list(
            new ListInvoicesQuery(
                ACTOR,
                TENANT_ID,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                BUSINESS_DATE,
                0,
                50,
                InvoiceSortField.ISSUE_DATE,
                SortDirection.DESC)))
        .thenThrow(
            new OperationsAccessDeniedException(
                OperationsTenantId.of(TENANT_ID), OperationsPermission.READ_INVOICES));

    mockMvc
        .perform(
            get(ENDPOINT + "/" + INVOICE_ID)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));

    mockMvc
        .perform(
            get(ENDPOINT).principal(AUTHENTICATION).param("businessDate", BUSINESS_DATE.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void shouldRejectInvalidEnumParametersAsAStableProblem() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("status", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVOICE_QUERY"));
  }

  @Test
  void shouldRejectInvalidPagingAsAStableProblem() throws Exception {
    when(listInvoicesUseCase.list(org.mockito.ArgumentMatchers.any()))
        .thenThrow(
            new InvalidInvoiceQueryException(
                "Invoice query page size must be between 1 and 100",
                new IllegalArgumentException()));

    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("businessDate", BUSINESS_DATE.toString())
                .param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INVOICE_QUERY"));
  }

  private static InvoiceResult result() {
    CurrencyCode eur = CurrencyCode.of("EUR");
    return new InvoiceResult(
        InvoiceId.of(INVOICE_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(CUSTOMER_ID),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", eur),
        Money.of("25.00", eur),
        Money.of("75.00", eur),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        BUSINESS_DATE,
        InvoiceStatus.PARTIALLY_PAID,
        InvoiceDueState.OVERDUE,
        false,
        true);
  }
}
