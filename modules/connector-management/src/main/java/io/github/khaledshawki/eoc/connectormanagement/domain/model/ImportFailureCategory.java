package io.github.khaledshawki.eoc.connectormanagement.domain.model;

public enum ImportFailureCategory {
  AUTHENTICATION_FAILED(false),
  AUTHORIZATION_FAILED(false),
  SOURCE_UNAVAILABLE(true),
  TIMEOUT(true),
  RATE_LIMITED(true),
  INVALID_CURSOR(false),
  SOURCE_CONTRACT_VIOLATION(false),
  DOWNSTREAM_UNAVAILABLE(true),
  UNEXPECTED_FAILURE(false);

  private final boolean retryable;

  ImportFailureCategory(boolean retryable) {
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }
}
