package io.github.khaledshawki.eoc.copilot.application.service;

import io.github.khaledshawki.eoc.copilot.application.exception.CopilotOrchestrationLimitExceededException;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotAnswer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotCustomer;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotMoney;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivable;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivableCurrencySummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablePage;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotReceivablesSummary;
import io.github.khaledshawki.eoc.copilot.application.model.CopilotToolObservation;
import java.util.List;

final class CopilotGroundedAnswerRenderer {

  private CopilotGroundedAnswerRenderer() {}

  static String render(List<CopilotToolObservation> observations) {
    StringBuilder answer = new StringBuilder();
    for (CopilotToolObservation observation : observations) {
      if (!answer.isEmpty()) {
        answer.append("\n\n");
      }
      switch (observation) {
        case CopilotToolObservation.Receivable item -> appendReceivable(answer, item.result());
        case CopilotToolObservation.ReceivablePage page -> appendPage(answer, page.result());
        case CopilotToolObservation.ReceivablesSummary summary ->
            appendSummary(answer, summary.result());
      }
      requireBounded(answer);
    }
    return answer.toString();
  }

  private static void appendReceivable(StringBuilder answer, CopilotReceivable receivable) {
    answer
        .append("Invoice ")
        .append(receivable.invoiceNumber())
        .append(" for ")
        .append(customer(receivable.customer()))
        .append(": status ")
        .append(receivable.status().name())
        .append("; outstanding ")
        .append(money(receivable.outstandingAmount()))
        .append(" of ")
        .append(money(receivable.originalAmount()))
        .append("; paid ")
        .append(money(receivable.paidAmount()))
        .append("; issued ")
        .append(receivable.issueDate())
        .append("; due ")
        .append(receivable.dueDate())
        .append("; ")
        .append(receivable.overdue() ? "overdue" : "not overdue")
        .append(" as of ")
        .append(receivable.businessDate())
        .append('.');
  }

  private static void appendPage(StringBuilder answer, CopilotReceivablePage page) {
    if (page.totalElements() == 0) {
      answer
          .append("Receivables query as of ")
          .append(page.businessDate())
          .append(" returned no matches.");
      return;
    }

    answer
        .append("Receivables query as of ")
        .append(page.businessDate())
        .append(": this page contains ")
        .append(page.receivables().size())
        .append(" of ")
        .append(page.totalElements())
        .append(" matching receivables.");
    for (CopilotReceivable receivable : page.receivables()) {
      answer.append("\n- ");
      appendReceivable(answer, receivable);
    }
  }

  private static void appendSummary(StringBuilder answer, CopilotReceivablesSummary summary) {
    answer
        .append("Receivables summary as of ")
        .append(summary.businessDate())
        .append(": ")
        .append(summary.invoiceCount())
        .append(" projected invoices; ")
        .append(summary.openCount())
        .append(" open; ")
        .append(summary.overdueCount())
        .append(" overdue.");

    for (CopilotReceivableCurrencySummary currency : summary.currencies()) {
      answer
          .append("\n- ")
          .append(currency.currency())
          .append(": ")
          .append(currency.invoiceCount())
          .append(" invoices; ")
          .append(currency.openCount())
          .append(" open; ")
          .append(currency.overdueCount())
          .append(" overdue; outstanding ")
          .append(money(currency.outstandingAmount()))
          .append("; overdue ")
          .append(money(currency.overdueAmount()))
          .append("; current ")
          .append(money(currency.currentAmount()))
          .append("; aging 1-30 ")
          .append(money(currency.days1To30OverdueAmount()))
          .append(", 31-60 ")
          .append(money(currency.days31To60OverdueAmount()))
          .append(", 61-90 ")
          .append(money(currency.days61To90OverdueAmount()))
          .append(", 91+ ")
          .append(money(currency.days91PlusOverdueAmount()))
          .append('.');
    }
  }

  private static String customer(CopilotCustomer customer) {
    if (!customer.projected()) {
      return customer.customerId().toString();
    }
    return customer.displayName().orElseThrow()
        + " ("
        + customer.partnerNumber().orElseThrow()
        + ")";
  }

  private static String money(CopilotMoney money) {
    return money.currency() + " " + money.amount().toPlainString();
  }

  private static void requireBounded(StringBuilder answer) {
    if (answer.length() > CopilotAnswer.MAX_TEXT_LENGTH) {
      throw new CopilotOrchestrationLimitExceededException();
    }
  }
}
