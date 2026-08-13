package io.github.khaledshawki.eoc.copilot.application.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public sealed interface CopilotToolObservation
    permits CopilotToolObservation.Receivable,
        CopilotToolObservation.ReceivablePage,
        CopilotToolObservation.ReceivablesSummary {

  String callId();

  CopilotToolName toolName();

  CopilotModelToolCall toolCall();

  List<CopilotEvidence> sourceEvidence();

  record Receivable(CopilotModelToolCall.GetReceivable toolCall, CopilotReceivable result)
      implements CopilotToolObservation {
    public Receivable {
      Objects.requireNonNull(toolCall, "Copilot get-receivable model tool call cannot be null");
      Objects.requireNonNull(result, "Copilot get-receivable observation result cannot be null");
    }

    @Override
    public String callId() {
      return toolCall.callId();
    }

    @Override
    public CopilotToolName toolName() {
      return CopilotToolName.GET_RECEIVABLE;
    }

    @Override
    public List<CopilotEvidence> sourceEvidence() {
      return List.of(result.evidence());
    }
  }

  record ReceivablePage(CopilotModelToolCall.ListReceivables toolCall, CopilotReceivablePage result)
      implements CopilotToolObservation {
    public ReceivablePage {
      Objects.requireNonNull(toolCall, "Copilot list-receivables model tool call cannot be null");
      Objects.requireNonNull(result, "Copilot list-receivables observation result cannot be null");
    }

    @Override
    public String callId() {
      return toolCall.callId();
    }

    @Override
    public CopilotToolName toolName() {
      return CopilotToolName.LIST_RECEIVABLES;
    }

    @Override
    public List<CopilotEvidence> sourceEvidence() {
      LinkedHashSet<CopilotEvidence> evidence = new LinkedHashSet<>();
      result.receivables().forEach(receivable -> evidence.add(receivable.evidence()));
      return List.copyOf(evidence);
    }
  }

  record ReceivablesSummary(
      CopilotModelToolCall.GetReceivablesSummary toolCall, CopilotReceivablesSummary result)
      implements CopilotToolObservation {
    public ReceivablesSummary {
      Objects.requireNonNull(toolCall, "Copilot summary model tool call cannot be null");
      Objects.requireNonNull(result, "Copilot summary observation result cannot be null");
    }

    @Override
    public String callId() {
      return toolCall.callId();
    }

    @Override
    public CopilotToolName toolName() {
      return CopilotToolName.GET_RECEIVABLES_SUMMARY;
    }

    @Override
    public List<CopilotEvidence> sourceEvidence() {
      return List.of();
    }
  }
}
