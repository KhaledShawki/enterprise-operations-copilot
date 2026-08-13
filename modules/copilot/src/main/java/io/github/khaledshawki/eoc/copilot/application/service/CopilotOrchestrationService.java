package io.github.khaledshawki.eoc.copilot.application.service;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotAnswerGroundingException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotModelProtocolException;
import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswerGrounding;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotExecutionContext;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelRequest;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelResponse;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelToolCall;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotModelTurn;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotQuestion;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolObservation;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivableToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.GetReceivablesSummaryToolRequest;
import io.github.khaledshawki.eoc.copilot.application.model.ListReceivablesToolRequest;
import io.github.khaledshawki.eoc.copilot.application.port.in.AskCopilotUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotModelPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CopilotOrchestrationService implements AskCopilotUseCase {
  static final int MAX_MODEL_ROUNDS = 4;
  static final int MAX_TOOL_CALLS = 6;
  static final int MAX_TOOL_CALLS_PER_ROUND = CopilotModelResponse.ToolCalls.MAX_TOOL_CALLS;

  private final CopilotModelPort modelPort;
  private final ExecuteCopilotToolUseCase toolUseCase;

  public CopilotOrchestrationService(
      CopilotModelPort modelPort, ExecuteCopilotToolUseCase toolUseCase) {
    this.modelPort = Objects.requireNonNull(modelPort, "Copilot model port cannot be null");
    this.toolUseCase = Objects.requireNonNull(toolUseCase, "Copilot tool use case cannot be null");
  }

  @Override
  public CopilotAnswer ask(CopilotExecutionContext context, CopilotQuestion question) {
    Objects.requireNonNull(context, "Copilot execution context cannot be null");
    Objects.requireNonNull(question, "Copilot question cannot be null");

    List<CopilotModelTurn> completedTurns = new ArrayList<>();
    Map<String, CopilotToolObservation> observationsByCallId = new LinkedHashMap<>();
    Set<GetReceivableToolRequest> getReceivableRequests = new HashSet<>();
    Set<ListReceivablesToolRequest> listReceivablesRequests = new HashSet<>();
    Set<GetReceivablesSummaryToolRequest> summaryRequests = new HashSet<>();
    int totalToolCalls = 0;

    for (int round = 0; round < MAX_MODEL_ROUNDS; round++) {
      CopilotModelResponse response =
          modelPort.generate(new CopilotModelRequest(question, completedTurns));
      if (response == null) {
        throw new CopilotModelProtocolException();
      }

      if (response instanceof CopilotModelResponse.Answer answer) {
        return groundedAnswer(answer, observationsByCallId);
      }

      CopilotModelResponse.ToolCalls toolCalls = (CopilotModelResponse.ToolCalls) response;
      if (toolCalls.toolCalls().size() > MAX_TOOL_CALLS_PER_ROUND
          || totalToolCalls + toolCalls.toolCalls().size() > MAX_TOOL_CALLS) {
        throw new CopilotOrchestrationLimitExceededException();
      }

      validateToolCalls(
          toolCalls.toolCalls(),
          observationsByCallId.keySet(),
          getReceivableRequests,
          listReceivablesRequests,
          summaryRequests);

      List<CopilotToolObservation> observations = new ArrayList<>(toolCalls.toolCalls().size());
      for (CopilotModelToolCall toolCall : toolCalls.toolCalls()) {
        CopilotToolObservation observation = execute(context, question, toolCall);
        observations.add(observation);
        observationsByCallId.put(observation.callId(), observation);
      }
      completedTurns.add(new CopilotModelTurn(observations));
      totalToolCalls += observations.size();
    }

    throw new CopilotOrchestrationLimitExceededException();
  }

  private static void validateToolCalls(
      List<CopilotModelToolCall> toolCalls,
      Set<String> completedCallIds,
      Set<GetReceivableToolRequest> getReceivableRequests,
      Set<ListReceivablesToolRequest> listReceivablesRequests,
      Set<GetReceivablesSummaryToolRequest> summaryRequests) {
    Set<String> currentCallIds = new HashSet<>();
    Set<GetReceivableToolRequest> currentGetRequests = new HashSet<>();
    Set<ListReceivablesToolRequest> currentListRequests = new HashSet<>();
    Set<GetReceivablesSummaryToolRequest> currentSummaryRequests = new HashSet<>();

    for (CopilotModelToolCall toolCall : toolCalls) {
      if (toolCall instanceof CopilotModelToolCall.Unsupported) {
        throw new CopilotModelProtocolException();
      }
      if (completedCallIds.contains(toolCall.callId()) || !currentCallIds.add(toolCall.callId())) {
        throw new CopilotModelProtocolException();
      }

      switch (toolCall) {
        case CopilotModelToolCall.GetReceivable call -> {
          requireModelDoesNotControlBusinessDate(call.request().businessDate());
          if (getReceivableRequests.contains(call.request())
              || !currentGetRequests.add(call.request())) {
            throw new CopilotModelProtocolException();
          }
        }
        case CopilotModelToolCall.ListReceivables call -> {
          requireModelDoesNotControlBusinessDate(call.request().businessDate());
          if (listReceivablesRequests.contains(call.request())
              || !currentListRequests.add(call.request())) {
            throw new CopilotModelProtocolException();
          }
        }
        case CopilotModelToolCall.GetReceivablesSummary call -> {
          requireModelDoesNotControlBusinessDate(call.request().businessDate());
          if (summaryRequests.contains(call.request())
              || !currentSummaryRequests.add(call.request())) {
            throw new CopilotModelProtocolException();
          }
        }
        case CopilotModelToolCall.Unsupported ignored -> throw new CopilotModelProtocolException();
      }
    }

    getReceivableRequests.addAll(currentGetRequests);
    listReceivablesRequests.addAll(currentListRequests);
    summaryRequests.addAll(currentSummaryRequests);
  }

  private static void requireModelDoesNotControlBusinessDate(Optional<LocalDate> businessDate) {
    if (businessDate.isPresent()) {
      throw new CopilotModelProtocolException();
    }
  }

  private CopilotToolObservation execute(
      CopilotExecutionContext context, CopilotQuestion question, CopilotModelToolCall toolCall) {
    return switch (toolCall) {
      case CopilotModelToolCall.GetReceivable call ->
          new CopilotToolObservation.Receivable(
              call,
              toolUseCase.execute(
                  context,
                  new GetReceivableToolRequest(
                      call.request().invoiceId(), question.businessDate())));
      case CopilotModelToolCall.ListReceivables call ->
          new CopilotToolObservation.ReceivablePage(
              call,
              toolUseCase.execute(
                  context,
                  new ListReceivablesToolRequest(
                      call.request().customerId(),
                      call.request().statuses(),
                      call.request().overdue(),
                      question.businessDate(),
                      call.request().pageNumber(),
                      call.request().pageSize(),
                      call.request().sortField(),
                      call.request().sortDirection())));
      case CopilotModelToolCall.GetReceivablesSummary call ->
          new CopilotToolObservation.ReceivablesSummary(
              call,
              toolUseCase.execute(
                  context, new GetReceivablesSummaryToolRequest(question.businessDate())));
      case CopilotModelToolCall.Unsupported ignored -> throw new CopilotModelProtocolException();
    };
  }

  private static CopilotAnswer groundedAnswer(
      CopilotModelResponse.Answer answer,
      Map<String, CopilotToolObservation> observationsByCallId) {
    if (observationsByCallId.isEmpty()) {
      throw new CopilotAnswerGroundingException();
    }

    List<CopilotAnswerGrounding> grounding = new ArrayList<>();
    List<CopilotToolObservation> selectedObservations = new ArrayList<>();
    for (String callId : answer.groundingToolCallIds()) {
      CopilotToolObservation observation = observationsByCallId.get(callId);
      if (observation == null) {
        throw new CopilotAnswerGroundingException();
      }
      selectedObservations.add(observation);
      grounding.add(
          new CopilotAnswerGrounding(
              observation.callId(), observation.toolName(), observation.sourceEvidence()));
    }
    return new CopilotAnswer(CopilotGroundedAnswerRenderer.render(selectedObservations), grounding);
  }
}
