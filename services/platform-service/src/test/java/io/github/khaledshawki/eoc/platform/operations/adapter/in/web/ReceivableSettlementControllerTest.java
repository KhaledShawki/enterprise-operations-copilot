package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationReplayConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableInvoiceAllocationCapacityExceededException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsActor;
import io.github.khaledshawki.eoc.operations.application.model.authorization.OperationsPermission;
import io.github.khaledshawki.eoc.operations.application.model.settlement.ReceivableAllocationResult;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.AllocateReceivablePaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementQuery;
import io.github.khaledshawki.eoc.operations.application.port.in.GetReceivableSettlementUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.PaymentResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ReceivableSettlementResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ReverseReceivableAllocationUseCase;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.CurrencyCode;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.Money;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentId;
import io.github.khaledshawki.eoc.operations.domain.model.PaymentStatus;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationId;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableAllocationState;
import io.github.khaledshawki.eoc.operations.domain.model.ReceivableSettlementId;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import java.lang.reflect.Method;
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

@WebMvcTest(controllers = ReceivableSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReceivableSettlementControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final UUID SETTLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
  private static final UUID ALLOCATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final String SUBJECT = "settlement-user";
  private static final OperationsActor ACTOR = new OperationsActor(ISSUER.toString(), SUBJECT);
  private static final AuthenticatedUser AUTHENTICATED_USER =
      new AuthenticatedUser(ISSUER, SUBJECT, Set.of("operations-manager"));
  private static final JwtAuthenticationToken AUTHENTICATION =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("receivable-settlement-controller-test-token")
              .header("alg", "none")
              .issuer(ISSUER.toString())
              .subject(SUBJECT)
              .build());
  private static final String ENDPOINT =
      "/api/v1/tenants/" + TENANT_ID + "/payments/" + PAYMENT_ID + "/receivable-settlement";
  private static final CurrencyCode EUR = CurrencyCode.of("EUR");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private GetReceivableSettlementUseCase getReceivableSettlementUseCase;
  @MockitoBean private AllocateReceivablePaymentUseCase allocateReceivablePaymentUseCase;
  @MockitoBean private ReverseReceivableAllocationUseCase reverseReceivableAllocationUseCase;
  @MockitoBean private JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  @BeforeEach
  void setUp() {
    when(jwtAuthenticatedUserMapper.map(AUTHENTICATION)).thenReturn(AUTHENTICATED_USER);
  }

  @Test
  void shouldProtectEveryPublicSettlementEndpointWithMethodAuthorization() {
    assertTrue(
        Arrays.stream(ReceivableSettlementController.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .allMatch(method -> method.isAnnotationPresent(PreAuthorize.class)));
  }

  @Test
  void shouldKeepAuditorReadOnlyAtTheHttpBoundary() {
    Method readMethod = method("getSettlement");
    Method allocateMethod = method("allocate");
    Method reverseMethod = method("reverse");

    assertTrue(readMethod.getAnnotation(PreAuthorize.class).value().contains("'auditor'"));
    assertFalse(allocateMethod.getAnnotation(PreAuthorize.class).value().contains("'auditor'"));
    assertFalse(reverseMethod.getAnnotation(PreAuthorize.class).value().contains("'auditor'"));
  }

  @Test
  void shouldReadPaymentRootedSettlementState() throws Exception {
    GetReceivableSettlementQuery query =
        new GetReceivableSettlementQuery(ACTOR, TENANT_ID, PAYMENT_ID);
    when(getReceivableSettlementUseCase.get(query)).thenReturn(settlementResult());

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment.id").value(PAYMENT_ID.toString()))
        .andExpect(jsonPath("$.settlementId").value(SETTLEMENT_ID.toString()))
        .andExpect(jsonPath("$.allocatedAmount.amount").value(25.00))
        .andExpect(jsonPath("$.allocatedAmount.currency").value("EUR"))
        .andExpect(jsonPath("$.unappliedAmount.amount").value(75.00))
        .andExpect(jsonPath("$.allocations.length()").value(1))
        .andExpect(jsonPath("$.allocations[0].id").value(ALLOCATION_ID.toString()))
        .andExpect(jsonPath("$.allocations[0].state").value("ACTIVE"));

    verify(getReceivableSettlementUseCase).get(query);
  }

  @Test
  void shouldExposeFullyUnappliedPaymentWhenNoSettlementExistsYet() throws Exception {
    GetReceivableSettlementQuery query =
        new GetReceivableSettlementQuery(ACTOR, TENANT_ID, PAYMENT_ID);
    when(getReceivableSettlementUseCase.get(query))
        .thenReturn(
            new ReceivableSettlementResult(
                paymentResult(),
                Optional.empty(),
                Money.zero(EUR),
                Money.of("100.00", EUR),
                List.of()));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settlementId").doesNotExist())
        .andExpect(jsonPath("$.allocatedAmount.amount").value(0.00))
        .andExpect(jsonPath("$.unappliedAmount.amount").value(100.00))
        .andExpect(jsonPath("$.allocations.length()").value(0));
  }

  @Test
  void shouldAllocateUsingCallerStableAllocationIdentityAndCanonicalMoney() throws Exception {
    AllocateReceivablePaymentCommand command =
        new AllocateReceivablePaymentCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, Money.of("25.00", EUR));
    when(allocateReceivablePaymentUseCase.allocate(command)).thenReturn(allocationResult());

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocationId": "%s",
                      "invoiceId": "%s",
                      "amount": {
                        "amount": 25.00,
                        "currency": "eur"
                      }
                    }
                    """
                        .formatted(ALLOCATION_ID, INVOICE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settlementId").value(SETTLEMENT_ID.toString()))
        .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
        .andExpect(jsonPath("$.allocation.id").value(ALLOCATION_ID.toString()))
        .andExpect(jsonPath("$.allocation.invoiceId").value(INVOICE_ID.toString()))
        .andExpect(jsonPath("$.allocation.amount.amount").value(25.00))
        .andExpect(jsonPath("$.allocation.amount.currency").value("EUR"))
        .andExpect(jsonPath("$.allocation.state").value("ACTIVE"));

    verify(allocateReceivablePaymentUseCase).allocate(command);
  }

  @Test
  void shouldReverseAllocationUsingPathIdentityAndInvoiceCoordinationKey() throws Exception {
    ReverseReceivableAllocationCommand command =
        new ReverseReceivableAllocationCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID);
    ReceivableAllocationResult reversed =
        new ReceivableAllocationResult(
            ReceivableSettlementId.of(SETTLEMENT_ID),
            PaymentId.of(PAYMENT_ID),
            ReceivableAllocationId.of(ALLOCATION_ID),
            InvoiceId.of(INVOICE_ID),
            Money.of("25.00", EUR),
            ReceivableAllocationState.REVERSED);
    when(reverseReceivableAllocationUseCase.reverse(command)).thenReturn(reversed);

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations/" + ALLOCATION_ID + "/reversal")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "invoiceId": "%s"
                    }
                    """
                        .formatted(INVOICE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocation.id").value(ALLOCATION_ID.toString()))
        .andExpect(jsonPath("$.allocation.state").value("REVERSED"));

    verify(reverseReceivableAllocationUseCase).reverse(command);
  }

  @Test
  void shouldRejectMalformedMoneyAsStableBadRequest() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocationId": "%s",
                      "invoiceId": "%s",
                      "amount": {
                        "amount": 25.001,
                        "currency": "EUR"
                      }
                    }
                    """
                        .formatted(ALLOCATION_ID, INVOICE_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_SETTLEMENT_REQUEST"));
  }

  @Test
  void shouldRejectUnreadableBodyAndInvalidPathIdentityAsStableBadRequests() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_SETTLEMENT_REQUEST"));

    String invalidPath =
        "/api/v1/tenants/" + TENANT_ID + "/payments/not-a-uuid/receivable-settlement";
    mockMvc
        .perform(get(invalidPath).principal(AUTHENTICATION))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_RECEIVABLE_SETTLEMENT_REQUEST"));
  }

  @Test
  void shouldMapReplayAndCapacityConflictsToStableProblems() throws Exception {
    AllocateReceivablePaymentCommand command =
        new AllocateReceivablePaymentCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, Money.of("25.00", EUR));
    when(allocateReceivablePaymentUseCase.allocate(command))
        .thenThrow(
            new ReceivableAllocationReplayConflictException(
                ReceivableAllocationId.of(ALLOCATION_ID), "different amount"))
        .thenThrow(
            new ReceivableInvoiceAllocationCapacityExceededException(
                InvoiceId.of(INVOICE_ID), Money.of("25.00", EUR), Money.of("10.00", EUR)));

    String requestBody =
        """
        {
          "allocationId": "%s",
          "invoiceId": "%s",
          "amount": {
            "amount": 25.00,
            "currency": "EUR"
          }
        }
        """
            .formatted(ALLOCATION_ID, INVOICE_ID);

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_ALLOCATION_REPLAY_CONFLICT"));

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_INVOICE_ALLOCATION_CAPACITY_EXCEEDED"));
  }

  @Test
  void shouldMapBusinessConflictAndCorruptedStateWithoutLeakingInternalState() throws Exception {
    AllocateReceivablePaymentCommand command =
        new AllocateReceivablePaymentCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, Money.of("25.00", EUR));
    when(allocateReceivablePaymentUseCase.allocate(command))
        .thenThrow(new IllegalArgumentException("Cannot allocate a reversed payment"));
    GetReceivableSettlementQuery query =
        new GetReceivableSettlementQuery(ACTOR, TENANT_ID, PAYMENT_ID);
    when(getReceivableSettlementUseCase.get(query))
        .thenThrow(
            new ReceivableSettlementStateCorruptedException(
                "sensitive persistence corruption detail"));

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocationId": "%s",
                      "invoiceId": "%s",
                      "amount": {
                        "amount": 25.00,
                        "currency": "EUR"
                      }
                    }
                    """
                        .formatted(ALLOCATION_ID, INVOICE_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_SETTLEMENT_CONFLICT"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_SETTLEMENT_STATE_CORRUPTED"))
        .andExpect(
            jsonPath("$.detail")
                .value("Receivable settlement state is inconsistent and cannot be processed."));
  }

  @Test
  void shouldMapMissingPaymentAndAllocationAndAccessDenial() throws Exception {
    GetReceivableSettlementQuery query =
        new GetReceivableSettlementQuery(ACTOR, TENANT_ID, PAYMENT_ID);
    when(getReceivableSettlementUseCase.get(query))
        .thenThrow(
            new PaymentNotFoundException(
                OperationsTenantId.of(TENANT_ID), PaymentId.of(PAYMENT_ID)))
        .thenThrow(
            new OperationsAccessDeniedException(
                OperationsTenantId.of(TENANT_ID),
                OperationsPermission.READ_RECEIVABLE_SETTLEMENTS));
    AllocateReceivablePaymentCommand allocateCommand =
        new AllocateReceivablePaymentCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID, Money.of("25.00", EUR));
    when(allocateReceivablePaymentUseCase.allocate(allocateCommand))
        .thenThrow(
            new InvoiceNotFoundException(
                OperationsTenantId.of(TENANT_ID), InvoiceId.of(INVOICE_ID)));
    ReverseReceivableAllocationCommand reverseCommand =
        new ReverseReceivableAllocationCommand(
            ACTOR, TENANT_ID, PAYMENT_ID, INVOICE_ID, ALLOCATION_ID);
    when(reverseReceivableAllocationUseCase.reverse(reverseCommand))
        .thenThrow(
            new ReceivableAllocationNotFoundException(
                OperationsTenantId.of(TENANT_ID), ReceivableAllocationId.of(ALLOCATION_ID)));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

    mockMvc
        .perform(get(ENDPOINT).principal(AUTHENTICATION))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "allocationId": "%s",
                      "invoiceId": "%s",
                      "amount": {
                        "amount": 25.00,
                        "currency": "EUR"
                      }
                    }
                    """
                        .formatted(ALLOCATION_ID, INVOICE_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));

    mockMvc
        .perform(
            post(ENDPOINT + "/allocations/" + ALLOCATION_ID + "/reversal")
                .principal(AUTHENTICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "invoiceId": "%s"
                    }
                    """
                        .formatted(INVOICE_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECEIVABLE_ALLOCATION_NOT_FOUND"));
  }

  private static Method method(String name) {
    return Arrays.stream(ReceivableSettlementController.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static ReceivableSettlementResult settlementResult() {
    return new ReceivableSettlementResult(
        paymentResult(),
        Optional.of(ReceivableSettlementId.of(SETTLEMENT_ID)),
        Money.of("25.00", EUR),
        Money.of("75.00", EUR),
        List.of(allocationResult()));
  }

  private static ReceivableAllocationResult allocationResult() {
    return new ReceivableAllocationResult(
        ReceivableSettlementId.of(SETTLEMENT_ID),
        PaymentId.of(PAYMENT_ID),
        ReceivableAllocationId.of(ALLOCATION_ID),
        InvoiceId.of(INVOICE_ID),
        Money.of("25.00", EUR),
        ReceivableAllocationState.ACTIVE);
  }

  private static PaymentResult paymentResult() {
    Money amount = Money.of("100.00", EUR);
    return new PaymentResult(
        PaymentId.of(PAYMENT_ID),
        OperationsTenantId.of(TENANT_ID),
        BusinessPartnerId.of(CUSTOMER_ID),
        amount,
        amount,
        LocalDate.of(2026, 8, 9),
        PaymentStatus.RECORDED,
        false);
  }
}
