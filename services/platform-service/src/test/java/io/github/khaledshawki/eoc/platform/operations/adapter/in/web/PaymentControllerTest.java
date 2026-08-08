package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.querying.PaymentSortField;
import io.github.khaledshawki.eoc.operations.application.model.querying.SortDirection;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentPageResult;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.LocalDate;
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

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final String SUBJECT = "payment-reader";
  private static final OperationsActor ACTOR = new OperationsActor(ISSUER.toString(), SUBJECT);
  private static final AuthenticatedUser AUTHENTICATED_USER =
      new AuthenticatedUser(ISSUER, SUBJECT, Set.of("auditor"));
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("payment-controller-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject(SUBJECT)
              .build());
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/payments";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetPaymentUseCase getPaymentUseCase;
  @MockitoBean private ListPaymentsUseCase listPaymentsUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  @BeforeEach
  void setUp() {
    when(jwtAuthenticatedUserMapper.map(AUTHENTICATION)).thenReturn(AUTHENTICATED_USER);
  }

  @Test
  void shouldProtectEveryPublicPaymentEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(PaymentController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void shouldGetPaymentWithCanonicalLifecycleFacts() throws Exception {
    when(getPaymentUseCase.get(new GetPaymentQuery(ACTOR, TENANT_ID, PAYMENT_ID)))
        .thenReturn(result(true));

    mockMvc
        .perform(
            get(ENDPOINT + "/" + PAYMENT_ID)
                .principal(AUTHENTICATION)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(PAYMENT_ID.toString()))
        .andExpect(jsonPath("$.status").value("REVERSED"))
        .andExpect(jsonPath("$.reversed").value(true))
        .andExpect(jsonPath("$.amount.amount").value(100.00))
        .andExpect(jsonPath("$.amount.currency").value("EUR"))
        .andExpect(jsonPath("$.effectiveAmount.amount").value(0.00))
        .andExpect(jsonPath("$.effectiveAmount.currency").value("EUR"));

    verify(getPaymentUseCase).get(new GetPaymentQuery(ACTOR, TENANT_ID, PAYMENT_ID));
  }

  @Test
  void shouldListPaymentsWithFiltersAndPaging() throws Exception {
    LocalDate from = LocalDate.of(2026, 8, 1);
    LocalDate to = LocalDate.of(2026, 8, 31);
    ListPaymentsQuery query =
        new ListPaymentsQuery(
            ACTOR,
            TENANT_ID,
            Optional.of(CUSTOMER_ID),
            Set.of(PaymentStatus.RECORDED, PaymentStatus.REVERSED),
            Optional.of(from),
            Optional.of(to),
            1,
            25,
            PaymentSortField.PAYMENT_DATE,
            SortDirection.ASC);
    when(listPaymentsUseCase.list(query))
        .thenReturn(new PaymentPageResult(List.of(result(false)), 1, 25, 26, 2, false, true));

    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("customerId", CUSTOMER_ID.toString())
                .param("status", "RECORDED", "REVERSED")
                .param("paymentDateFrom", from.toString())
                .param("paymentDateTo", to.toString())
                .param("page", "1")
                .param("size", "25")
                .param("sort", "PAYMENT_DATE")
                .param("direction", "ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments.length()").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(26))
        .andExpect(jsonPath("$.hasPrevious").value(true));

    verify(listPaymentsUseCase).list(query);
  }

  @Test
  void shouldUseStableListDefaults() throws Exception {
    ListPaymentsQuery query =
        new ListPaymentsQuery(
            ACTOR,
            TENANT_ID,
            Optional.empty(),
            Set.of(),
            Optional.empty(),
            Optional.empty(),
            0,
            50,
            PaymentSortField.PAYMENT_DATE,
            SortDirection.DESC);
    when(listPaymentsUseCase.list(query))
        .thenReturn(new PaymentPageResult(List.of(), 0, 50, 0, 0, false, false));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payments.length()").value(0));

    verify(listPaymentsUseCase).list(query);
  }

  @Test
  void shouldReturnPaymentProblemDetails() throws Exception {
    when(getPaymentUseCase.get(new GetPaymentQuery(ACTOR, TENANT_ID, PAYMENT_ID)))
        .thenThrow(
            new PaymentNotFoundException(
                OperationsTenantId.of(TENANT_ID), PaymentId.of(PAYMENT_ID)));
    when(listPaymentsUseCase.list(
            new ListPaymentsQuery(
                ACTOR,
                TENANT_ID,
                Optional.empty(),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                0,
                50,
                PaymentSortField.PAYMENT_DATE,
                SortDirection.DESC)))
        .thenThrow(
            new OperationsAccessDeniedException(
                OperationsTenantId.of(TENANT_ID), OperationsPermission.READ_PAYMENTS));

    mockMvc
        .perform(get(ENDPOINT + "/" + PAYMENT_ID).principal(AUTHENTICATION))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void shouldRejectInvalidEnumAndDateRangeAsStableProblems() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).param("status", "UNKNOWN"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_QUERY"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .principal(AUTHENTICATION)
                .param("paymentDateFrom", "2026-08-31")
                .param("paymentDateTo", "2026-08-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_QUERY"));
  }

  @Test
  void shouldRejectInvalidPagingAsAStableProblem() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_QUERY"));
  }

  private static PaymentResult result(boolean reversed) {
    CurrencyCode eur = CurrencyCode.of("EUR");
    Money amount = Money.of("100.00", eur);
    return new PaymentResult(
        PaymentId.of(PAYMENT_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(CUSTOMER_ID),
        amount,
        reversed ? Money.zero(eur) : amount,
        LocalDate.of(2026, 8, 6),
        reversed ? PaymentStatus.REVERSED : PaymentStatus.RECORDED,
        reversed);
  }
}
