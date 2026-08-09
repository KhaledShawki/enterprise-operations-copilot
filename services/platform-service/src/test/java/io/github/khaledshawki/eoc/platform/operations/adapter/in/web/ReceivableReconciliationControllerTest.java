package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableReconciliationStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationIssue;
import io.github.khaledshawki.eoc.operations.application.model.reconciliation.ReceivableReconciliationStatus;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableReconciliationUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableReconciliationResult;
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
import java.util.Arrays;
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

@WebMvcTest(controllers = ReceivableReconciliationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceivableReconciliationControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final String SUBJECT = "reconciliation-reader";
  private static final OperationsActor ACTOR = new OperationsActor(ISSUER.toString(), SUBJECT);
  private static final AuthenticatedUser AUTHENTICATED_USER =
      new AuthenticatedUser(ISSUER, SUBJECT, Set.of("auditor"));
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("receivable-reconciliation-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject(SUBJECT)
              .build());
  private static final String ENDPOINT =
      "/api/v1/tenants/" + TENANT_ID + "/invoices/" + INVOICE_ID + "/receivable-reconciliation";
  private static final GetReceivableReconciliationQuery QUERY =
      new GetReceivableReconciliationQuery(ACTOR, TENANT_ID, INVOICE_ID);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetReceivableReconciliationUseCase getReceivableReconciliationUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  @BeforeEach
  void setUp() {
    when(jwtAuthenticatedUserMapper.map(AUTHENTICATION)).thenReturn(AUTHENTICATED_USER);
  }

  @Test
  void shouldProtectEveryPublicEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(ReceivableReconciliationController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void shouldReturnMatchedReconciliationEvidence() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenReturn(
            result(
                "40.00",
                Optional.of("40.00"),
                Optional.of("0.00"),
                ReceivableReconciliationStatus.MATCHED,
                Set.of(),
                false,
                2));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoiceId").value(INVOICE_ID.toString()))
        .andExpect(jsonPath("$.sourcePaidAmount.amount").value(40.00))
        .andExpect(jsonPath("$.localAllocatedAmount.amount").value(40.00))
        .andExpect(jsonPath("$.difference.amount").value(0.00))
        .andExpect(jsonPath("$.status").value("MATCHED"))
        .andExpect(jsonPath("$.activeAllocationCount").value(2))
        .andExpect(jsonPath("$.issues.length()").value(0));

    verify(getReceivableReconciliationUseCase).get(QUERY);
  }

  @Test
  void shouldReturnStructuralConflictAsEvidenceInsteadOfHttpConflict() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenReturn(
            result(
                "40.00",
                Optional.of("40.00"),
                Optional.empty(),
                ReceivableReconciliationStatus.CONFLICT,
                Set.of(ReceivableReconciliationIssue.PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS),
                false,
                1));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFLICT"))
        .andExpect(jsonPath("$.difference").doesNotExist())
        .andExpect(jsonPath("$.issues[0]").value("PAYMENT_REVERSED_WITH_ACTIVE_ALLOCATIONS"));
  }

  @Test
  void shouldRepresentUncomparableAllocationCurrencyWithoutInventingLocalAmount() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenReturn(
            result(
                "40.00",
                Optional.empty(),
                Optional.empty(),
                ReceivableReconciliationStatus.CONFLICT,
                Set.of(ReceivableReconciliationIssue.ALLOCATION_CURRENCY_MISMATCH),
                false,
                1));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.localAllocatedAmount").doesNotExist())
        .andExpect(jsonPath("$.difference").doesNotExist())
        .andExpect(jsonPath("$.status").value("CONFLICT"));
  }

  @Test
  void shouldReturnStableNotFoundProblem() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenThrow(
            new InvoiceNotFoundException(
                OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID)));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
  }

  @Test
  void shouldReturnStableAccessDeniedProblem() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenThrow(
            new OperationsAccessDeniedException(
                OperationsTenantId.of(TENANT_ID),
                OperationsPermission.READ_RECEIVABLE_RECONCILIATIONS));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void shouldNotLeakInternalCorruptionDetails() throws Exception {
    when(getReceivableReconciliationUseCase.get(QUERY))
        .thenThrow(
            new ReceivableReconciliationStateCorruptedException(
                "sensitive database reconciliation detail"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_RECONCILIATION_STATE_CORRUPTED"))
        .andExpect(
            jsonPath("$.detail")
                .value("Receivable reconciliation state is inconsistent and cannot be processed."));
  }

  @Test
  void shouldRejectMalformedInvoiceIdAsStableProblem() throws Exception {
    String malformed =
        "/api/v1/tenants/" + TENANT_ID + "/invoices/not-a-uuid/receivable-reconciliation";

    mockMvc
        .perform(get(malformed).principal(AUTHENTICATION))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_RECONCILIATION_REQUEST"));
  }

  private static ReceivableReconciliationResult result(
      String sourcePaid,
      Optional<String> local,
      Optional<String> difference,
      ReceivableReconciliationStatus status,
      Set<ReceivableReconciliationIssue> issues,
      boolean cancelled,
      long activeAllocationCount) {
    CurrencyCode eur = CurrencyCode.of("EUR");
    return new ReceivableReconciliationResult(
        InvoiceId.of(INVOICE_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(CUSTOMER_ID),
        new InvoiceNumber("INV-1"),
        Money.of("100.00", eur),
        Money.of(sourcePaid, eur),
        local.map(value -> Money.of(value, eur)),
        difference.map(value -> Money.of(value, eur)),
        cancelled
            ? InvoiceStatus.CANCELLED
            : Money.of(sourcePaid, eur).isPositive()
                ? InvoiceStatus.PARTIALLY_PAID
                : InvoiceStatus.OPEN,
        cancelled,
        activeAllocationCount,
        status,
        issues);
  }
}
