package io.github.khaledshawki.eoc.copilot.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotAnswerGroundingException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelUnavailableException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotEvidence;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelRequest;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelToolCall;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
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
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotModelPort;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CopilotOrchestrationServiceTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000211");
  private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 13);
  private static final CopilotExecutionContext CONTEXT =
      new CopilotExecutionContext(URI.create("https://issuer.example"), "subject-1", TENANT_ID);
  private static final CopilotQuestion QUESTION =
      CopilotQuestion.current("Which receivable needs follow-up?");

  @Test
  void executesApprovedToolWithTrustedContextAndReturnsSourceGrounding() {
    GetReceivableToolRequest request = GetReceivableToolRequest.current(INVOICE_ID);
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivable("call-1", request))),
            new CopilotModelResponse.Answer(List.of("call-1")));
    var tools = new RecordingTools();
    tools.receivable = receivable(INVOICE_ID);

    var service = new CopilotOrchestrationService(model, tools);
    var answer = service.ask(CONTEXT, QUESTION);

    assertEquals(
        "Invoice INV-1 for Example Customer (C-1): status PARTIALLY_PAID; outstanding CHF 80.00 of CHF 100.00; paid CHF 20.00; issued 2026-07-01; due 2026-08-01; overdue as of 2026-08-13.",
        answer.text());
    assertEquals(1, answer.grounding().size());
    assertEquals("call-1", answer.grounding().getFirst().toolCallId());
    assertEquals(EVENT_ID, answer.grounding().getFirst().sourceEvidence().getFirst().eventId());
    assertSame(CONTEXT, tools.lastContext);
    assertEquals(request, tools.lastGetRequest);
    assertEquals(2, model.requests.size());
    assertTrue(model.requests.getFirst().completedTurns().isEmpty());
    assertEquals(1, model.requests.get(1).completedTurns().size());
    assertEquals(
        "call-1",
        model.requests.get(1).completedTurns().getFirst().observations().getFirst().callId());
  }

  @Test
  void injectsTrustedQuestionBusinessDateInsteadOfLettingTheModelChooseIt() {
    LocalDate selectedDate = LocalDate.of(2026, 8, 5);
    var question =
        new CopilotQuestion(
            "Show the receivable at the selected business date", Optional.of(selectedDate));
    var modelRequest = GetReceivableToolRequest.current(INVOICE_ID);
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivable("call-1", modelRequest))),
            new CopilotModelResponse.Answer(List.of("call-1")));
    var tools = new RecordingTools();
    tools.receivable = receivable(INVOICE_ID, selectedDate);

    new CopilotOrchestrationService(model, tools).ask(CONTEXT, question);

    assertEquals(Optional.of(selectedDate), tools.lastGetRequest.businessDate());
    assertTrue(modelRequest.businessDate().isEmpty());
  }

  @Test
  void rejectsModelSuppliedBusinessDateBeforeToolExecution() {
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(
                    new CopilotModelToolCall.GetReceivablesSummary(
                        "summary-1",
                        new GetReceivablesSummaryToolRequest(
                            Optional.of(LocalDate.of(2026, 1, 1)))))));
    var tools = new RecordingTools();

    assertThrows(
        CopilotModelProtocolException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(0, tools.executions.get());
  }

  @Test
  void acceptsGroundingToDeterministicSummaryWithoutInventingSourceEventEvidence() {
    var request = GetReceivablesSummaryToolRequest.current();
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-1", request))),
            new CopilotModelResponse.Answer(List.of("summary-1")));
    var tools = new RecordingTools();
    tools.summary = new CopilotReceivablesSummary(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of());

    var answer = new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION);

    assertEquals(
        "Receivables summary as of 2026-08-13: 0 projected invoices; 0 open; 0 overdue.",
        answer.text());
    assertEquals(1, answer.grounding().size());
    assertTrue(answer.grounding().getFirst().sourceEvidence().isEmpty());
  }

  @Test
  void rendersCurrencyCountsBalancesAndAgingFromTypedSummary() {
    var request = GetReceivablesSummaryToolRequest.current();
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-1", request))),
            new CopilotModelResponse.Answer(List.of("summary-1")));
    var tools = new RecordingTools();
    tools.summary =
        new CopilotReceivablesSummary(
            TENANT_ID,
            BUSINESS_DATE,
            2,
            2,
            1,
            List.of(
                new CopilotReceivableCurrencySummary(
                    "CHF",
                    2,
                    2,
                    1,
                    new CopilotMoney(new BigDecimal("150.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("80.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("70.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("80.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("0.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("0.00"), "CHF"),
                    new CopilotMoney(new BigDecimal("0.00"), "CHF"))));

    var answer = new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION);

    assertEquals(
        "Receivables summary as of 2026-08-13: 2 projected invoices; 2 open; 1 overdue.\n"
            + "- CHF: 2 invoices; 2 open; 1 overdue; outstanding CHF 150.00; overdue CHF 80.00; "
            + "current CHF 70.00; aging 1-30 CHF 80.00, 31-60 CHF 0.00, 61-90 CHF 0.00, "
            + "91+ CHF 0.00.",
        answer.text());
    assertTrue(answer.grounding().getFirst().sourceEvidence().isEmpty());
  }

  @Test
  void rendersListFactsFromTypedObservationInsteadOfModelProse() {
    var request = listRequest(0);
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.ListReceivables("list-1", request))),
            new CopilotModelResponse.Answer(List.of("list-1")));
    var tools = new RecordingTools();
    tools.page =
        new CopilotReceivablePage(
            List.of(receivable(INVOICE_ID)), 0, 10, 1, 1, BUSINESS_DATE, false, false);

    var answer = new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION);

    assertTrue(
        answer
            .text()
            .startsWith(
                "Receivables query as of 2026-08-13: this page contains 1 of 1 matching receivables."));
    assertTrue(answer.text().contains("outstanding CHF 80.00"));
    assertTrue(answer.text().contains("status PARTIALLY_PAID"));
    assertEquals(EVENT_ID, answer.grounding().getFirst().sourceEvidence().getFirst().eventId());
  }

  @Test
  void rejectsBusinessAnswerBeforeAnyDeterministicToolExecution() {
    var model = new SequenceModel(new CopilotModelResponse.Answer(List.of("invented-call")));
    var tools = new RecordingTools();

    assertThrows(
        CopilotAnswerGroundingException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(0, tools.executions.get());
  }

  @Test
  void rejectsUnsupportedToolBeforeExecutingAnyPartOfTheRound() {
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(
                    new CopilotModelToolCall.GetReceivablesSummary(
                        "summary-1", GetReceivablesSummaryToolRequest.current()),
                    new CopilotModelToolCall.Unsupported("write-1", "create_invoice"))));
    var tools = new RecordingTools();

    assertThrows(
        CopilotModelProtocolException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(0, tools.executions.get());
  }

  @Test
  void rejectsRepeatedToolRequestAcrossRoundsInsteadOfLooping() {
    var request = GetReceivablesSummaryToolRequest.current();
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-1", request))),
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-2", request))));
    var tools = new RecordingTools();
    tools.summary = new CopilotReceivablesSummary(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of());

    assertThrows(
        CopilotModelProtocolException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(1, tools.executions.get());
  }

  @Test
  void rejectsDuplicateCallIdAcrossRounds() {
    var first = GetReceivablesSummaryToolRequest.current();
    var second = new GetReceivablesSummaryToolRequest(Optional.of(LocalDate.of(2026, 8, 12)));
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-1", first))),
            new CopilotModelResponse.ToolCalls(
                List.of(new CopilotModelToolCall.GetReceivablesSummary("summary-1", second))));
    var tools = new RecordingTools();
    tools.summary = new CopilotReceivablesSummary(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of());

    assertThrows(
        CopilotModelProtocolException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(1, tools.executions.get());
  }

  @Test
  void rejectsModelResponseAbovePerRoundToolBudget() {
    List<CopilotModelToolCall> calls =
        List.of(getCall("get-1", 1), getCall("get-2", 2), getCall("get-3", 3), getCall("get-4", 4));

    assertThrows(IllegalArgumentException.class, () -> new CopilotModelResponse.ToolCalls(calls));
  }

  @Test
  void enforcesTotalToolBudgetBeforeExecutingTheOverflowingRound() {
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(getCall("get-1", 1), getCall("get-2", 2), getCall("get-3", 3))),
            new CopilotModelResponse.ToolCalls(
                List.of(getCall("get-4", 4), getCall("get-5", 5), getCall("get-6", 6))),
            new CopilotModelResponse.ToolCalls(List.of(getCall("get-7", 7))));
    var tools = new RecordingTools();

    assertThrows(
        CopilotOrchestrationLimitExceededException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(CopilotOrchestrationService.MAX_TOOL_CALLS, tools.executions.get());
  }

  @Test
  void rejectsFinalGroundingThatReferencesAnUnexecutedCall() {
    var model =
        new SequenceModel(
            new CopilotModelResponse.ToolCalls(
                List.of(
                    new CopilotModelToolCall.GetReceivablesSummary(
                        "summary-1", GetReceivablesSummaryToolRequest.current()))),
            new CopilotModelResponse.Answer(List.of("other-call")));
    var tools = new RecordingTools();
    tools.summary = new CopilotReceivablesSummary(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of());

    assertThrows(
        CopilotAnswerGroundingException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
  }

  @Test
  void stopsAfterBoundedModelRoundsEvenWhenEveryRequestIsUnique() {
    List<CopilotModelResponse> responses = new ArrayList<>();
    for (int page = 0; page < CopilotOrchestrationService.MAX_MODEL_ROUNDS; page++) {
      responses.add(
          new CopilotModelResponse.ToolCalls(
              List.of(
                  new CopilotModelToolCall.ListReceivables("list-" + page, listRequest(page)))));
    }
    var model = new SequenceModel(responses.toArray(CopilotModelResponse[]::new));
    var tools = new RecordingTools();
    tools.page = new CopilotReceivablePage(List.of(), 0, 10, 0, 0, BUSINESS_DATE, false, false);

    assertThrows(
        CopilotOrchestrationLimitExceededException.class,
        () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals(CopilotOrchestrationService.MAX_MODEL_ROUNDS, tools.executions.get());
  }

  @Test
  void propagatesStableModelUnavailableFailure() {
    CopilotModelPort model =
        request -> {
          throw new CopilotModelUnavailableException(new IllegalStateException("provider-secret"));
        };
    var tools = new RecordingTools();

    var failure =
        assertThrows(
            CopilotModelUnavailableException.class,
            () -> new CopilotOrchestrationService(model, tools).ask(CONTEXT, QUESTION));
    assertEquals("Copilot language model is unavailable", failure.getMessage());
    assertEquals(0, tools.executions.get());
  }

  private static CopilotModelToolCall.GetReceivable getCall(String callId, int suffix) {
    UUID invoiceId = UUID.fromString("00000000-0000-0000-0000-%012d".formatted(500 + suffix));
    return new CopilotModelToolCall.GetReceivable(
        callId, GetReceivableToolRequest.current(invoiceId));
  }

  private static ListReceivablesToolRequest listRequest(int page) {
    return new ListReceivablesToolRequest(
        Optional.empty(),
        Set.of(),
        Optional.empty(),
        Optional.empty(),
        page,
        10,
        ReceivableSortField.DUE_DATE,
        SortDirection.ASC);
  }

  private static CopilotReceivable receivable(UUID invoiceId) {
    return receivable(invoiceId, BUSINESS_DATE);
  }

  private static CopilotReceivable receivable(UUID invoiceId, LocalDate businessDate) {
    return new CopilotReceivable(
        TENANT_ID,
        invoiceId,
        new CopilotCustomer(
            UUID.fromString("00000000-0000-0000-0000-000000000311"),
            true,
            Optional.of("C-1"),
            Optional.of("Example Customer")),
        "INV-1",
        new CopilotMoney(new BigDecimal("100.00"), "CHF"),
        new CopilotMoney(new BigDecimal("20.00"), "CHF"),
        new CopilotMoney(new BigDecimal("80.00"), "CHF"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 8, 1),
        businessDate,
        ReceivableStatus.PARTIALLY_PAID,
        false,
        true,
        new CopilotEvidence(EVENT_ID, 3, Instant.parse("2026-08-12T10:00:00Z")));
  }

  private static final class SequenceModel implements CopilotModelPort {
    private final Deque<CopilotModelResponse> responses = new ArrayDeque<>();
    private final List<CopilotModelRequest> requests = new ArrayList<>();

    private SequenceModel(CopilotModelResponse... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    public CopilotModelResponse generate(CopilotModelRequest request) {
      requests.add(request);
      if (responses.isEmpty()) {
        throw new AssertionError("unexpected model request");
      }
      return responses.removeFirst();
    }
  }

  private static final class RecordingTools implements ExecuteCopilotToolUseCase {
    private final AtomicInteger executions = new AtomicInteger();
    private CopilotExecutionContext lastContext;
    private GetReceivableToolRequest lastGetRequest;
    private CopilotReceivable receivable;
    private CopilotReceivablePage page;
    private CopilotReceivablesSummary summary;

    @Override
    public CopilotReceivable execute(
        CopilotExecutionContext context, GetReceivableToolRequest request) {
      executions.incrementAndGet();
      lastContext = context;
      lastGetRequest = request;
      if (receivable == null) {
        receivable = CopilotOrchestrationServiceTest.receivable(request.invoiceId());
      }
      return receivable;
    }

    @Override
    public CopilotReceivablePage execute(
        CopilotExecutionContext context, ListReceivablesToolRequest request) {
      executions.incrementAndGet();
      lastContext = context;
      if (page == null || page.pageNumber() != request.pageNumber()) {
        page =
            new CopilotReceivablePage(
                List.of(),
                request.pageNumber(),
                request.pageSize(),
                0,
                0,
                BUSINESS_DATE,
                false,
                false);
      }
      return page;
    }

    @Override
    public CopilotReceivablesSummary execute(
        CopilotExecutionContext context, GetReceivablesSummaryToolRequest request) {
      executions.incrementAndGet();
      lastContext = context;
      if (summary == null) {
        summary = new CopilotReceivablesSummary(TENANT_ID, BUSINESS_DATE, 0, 0, 0, List.of());
      }
      return summary;
    }
  }
}
