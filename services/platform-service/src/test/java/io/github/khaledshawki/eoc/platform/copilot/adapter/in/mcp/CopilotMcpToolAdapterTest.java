package io.github.khaledshawki.eoc.platform.copilot.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotToolAccessDeniedException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivableCurrencySummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableSortField;
import io.github.khaledshawki.eoc.copilot.application.model.ReceivableStatus;
import io.github.khaledshawki.eoc.copilot.application.model.SortDirection;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CopilotMcpToolAdapterTest {

  private static final URI ISSUER = URI.create("https://identity.example.com/realms/eoc");
  private static final String SUBJECT = "mcp-user";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 12);

  private ExecuteCopilotToolUseCase useCase;
  private CopilotMcpToolAdapter adapter;

  @BeforeEach
  void setUp() {
    useCase = Mockito.mock(ExecuteCopilotToolUseCase.class);
    adapter =
        new CopilotMcpToolAdapter(
            useCase, new CopilotMcpExecutionContextFactory(new JwtAuthenticatedUserMapper()));
  }

  @Test
  void delegatesGetReceivableToDeterministicUseCaseAndPreservesEvidence() {
    CopilotExecutionContext context = new CopilotExecutionContext(ISSUER, SUBJECT, TENANT_ID);
    GetReceivableToolRequest request =
        new GetReceivableToolRequest(INVOICE_ID, Optional.of(BUSINESS_DATE));
    when(useCase.execute(context, request)).thenReturn(receivable());

    var result =
        adapter.getReceivable(
            transportContext(TENANT_ID.toString()),
            INVOICE_ID.toString(),
            BUSINESS_DATE.toString());

    assertEquals(INVOICE_ID, result.invoiceId());
    assertEquals(new BigDecimal("75.00"), result.outstandingAmount().amount());
    assertEquals(2, result.evidence().aggregateVersion());
    verify(useCase).execute(context, request);
  }

  @Test
  void defaultsListArgumentsToExistingBoundedRequestContract() {
    CopilotExecutionContext context = new CopilotExecutionContext(ISSUER, SUBJECT, TENANT_ID);
    ListReceivablesToolRequest request =
        new ListReceivablesToolRequest(
            Optional.empty(),
            Set.of(),
            Optional.empty(),
            Optional.empty(),
            0,
            ListReceivablesToolRequest.MAX_PAGE_SIZE,
            ReceivableSortField.DUE_DATE,
            SortDirection.ASC);
    when(useCase.execute(context, request)).thenReturn(page());

    var result =
        adapter.listReceivables(
            transportContext(TENANT_ID.toString()), null, null, null, null, null, null, null, null);

    assertEquals(1, result.receivables().size());
    assertEquals(ListReceivablesToolRequest.MAX_PAGE_SIZE, result.pageSize());
    verify(useCase).execute(context, request);
  }

  @Test
  void returnsCurrencySeparatedSummaryOutput() {
    CopilotExecutionContext context = new CopilotExecutionContext(ISSUER, SUBJECT, TENANT_ID);
    GetReceivablesSummaryToolRequest request =
        new GetReceivablesSummaryToolRequest(Optional.of(BUSINESS_DATE));
    when(useCase.execute(context, request)).thenReturn(summary());

    var result =
        adapter.getReceivablesSummary(
            transportContext(TENANT_ID.toString()), BUSINESS_DATE.toString());

    assertEquals(TENANT_ID, result.tenantId());
    assertEquals("EUR", result.currencies().getFirst().currency());
    verify(useCase).execute(context, request);
  }

  @Test
  void rejectsMissingTenantBeforeCallingUseCase() {
    McpTransportContext context =
        McpTransportContext.create(
            Map.of(
                CopilotMcpTransportContextExtractor.AUTHENTICATION_CONTEXT_KEY, authentication()));

    CopilotMcpToolException exception =
        assertThrows(
            CopilotMcpToolException.class, () -> adapter.getReceivablesSummary(context, null));

    assertEquals(CopilotMcpToolException.INVALID_CONTEXT, exception.code());
    verifyNoInteractions(useCase);
  }

  @Test
  void mapsAccessDeniedToStableMcpError() {
    when(useCase.execute(
            new CopilotExecutionContext(ISSUER, SUBJECT, TENANT_ID),
            new GetReceivablesSummaryToolRequest(Optional.empty())))
        .thenThrow(new CopilotToolAccessDeniedException());

    CopilotMcpToolException exception =
        assertThrows(
            CopilotMcpToolException.class,
            () -> adapter.getReceivablesSummary(transportContext(TENANT_ID.toString()), null));

    assertEquals(CopilotMcpToolException.ACCESS_DENIED, exception.code());
    assertEquals("ACCESS_DENIED: Copilot tool access is denied", exception.getMessage());
  }

  @Test
  void hidesUnexpectedInternalFailureDetails() {
    when(useCase.execute(
            new CopilotExecutionContext(ISSUER, SUBJECT, TENANT_ID),
            new GetReceivablesSummaryToolRequest(Optional.empty())))
        .thenThrow(new IllegalStateException("secret database failure detail"));

    CopilotMcpToolException exception =
        assertThrows(
            CopilotMcpToolException.class,
            () -> adapter.getReceivablesSummary(transportContext(TENANT_ID.toString()), null));

    assertEquals(CopilotMcpToolException.INTERNAL_ERROR, exception.code());
    assertEquals("INTERNAL_ERROR: Copilot tool execution failed", exception.getMessage());
    assertFalse(exception.getMessage().contains("secret"));
  }

  private static McpTransportContext transportContext(String tenantId) {
    return McpTransportContext.create(
        Map.of(
            CopilotMcpTransportContextExtractor.AUTHENTICATION_CONTEXT_KEY,
            authentication(),
            CopilotMcpTransportContextExtractor.TENANT_CONTEXT_KEY,
            tenantId));
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("copilot-mcp-tool-test-token")
            .header("alg", "none")
            .issuer(ISSUER.toString())
            .subject(SUBJECT)
            .build();
    return new JwtAuthenticationToken(jwt, List.of());
  }

  private static CopilotReceivable receivable() {
    return new CopilotReceivable(
        TENANT_ID,
        INVOICE_ID,
        new CopilotCustomer(CUSTOMER_ID, true, Optional.of("C-100"), Optional.of("Acme AG")),
        "INV-100",
        money("100.00"),
        money("25.00"),
        money("75.00"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        BUSINESS_DATE,
        ReceivableStatus.PARTIALLY_PAID,
        false,
        true,
        new CopilotEvidence(
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            2,
            Instant.parse("2026-08-12T10:00:00Z")));
  }

  private static CopilotReceivablePage page() {
    return new CopilotReceivablePage(
        List.of(receivable()),
        0,
        ListReceivablesToolRequest.MAX_PAGE_SIZE,
        1,
        1,
        BUSINESS_DATE,
        false,
        false);
  }

  private static CopilotReceivablesSummary summary() {
    CopilotReceivableCurrencySummary currencySummary =
        new CopilotReceivableCurrencySummary(
            "EUR",
            1,
            1,
            1,
            money("75.00"),
            money("75.00"),
            money("0.00"),
            money("75.00"),
            money("0.00"),
            money("0.00"),
            money("0.00"));
    return new CopilotReceivablesSummary(
        TENANT_ID, BUSINESS_DATE, 1, 1, 1, List.of(currencySummary));
  }

  private static CopilotMoney money(String amount) {
    return new CopilotMoney(new BigDecimal(amount), "EUR");
  }
}
