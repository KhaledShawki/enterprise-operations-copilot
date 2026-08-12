package io.github.khaledshawki.eoc.copilot.application.model;

public enum CopilotToolName {
  GET_RECEIVABLE("get_receivable"),
  LIST_RECEIVABLES("list_receivables"),
  GET_RECEIVABLES_SUMMARY("get_receivables_summary");

  private final String contractName;

  CopilotToolName(String contractName) {
    this.contractName = contractName;
  }

  public String contractName() {
    return contractName;
  }
}
