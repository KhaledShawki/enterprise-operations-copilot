package io.github.khaledshawki.eoc.connectormanagement.domain.model;

public enum ImportStatus {
  REQUESTED(false),
  RUNNING(false),
  RETRY_SCHEDULED(false),
  CANCELLING(false),
  COMPLETED(true),
  PARTIALLY_COMPLETED(true),
  FAILED(true),
  CANCELLED(true);

  private final boolean terminal;

  ImportStatus(boolean terminal) {
    this.terminal = terminal;
  }

  public boolean terminal() {
    return terminal;
  }
}
