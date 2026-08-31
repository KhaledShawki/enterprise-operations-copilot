package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource.mockerp;

final class MockErpRpcException extends RuntimeException {

  enum Category {
    AUTHENTICATION_FAILED,
    AUTHORIZATION_FAILED,
    SOURCE_UNAVAILABLE,
    TIMEOUT,
    RATE_LIMITED,
    INVALID_POSITION,
    SOURCE_CONTRACT_VIOLATION,
    UNEXPECTED_SOURCE_FAILURE
  }

  private final Category category;
  private final String diagnosticCode;

  MockErpRpcException(Category category, String diagnosticCode, Throwable cause) {
    super(diagnosticCode, cause);
    this.category =
        java.util.Objects.requireNonNull(category, "RPC failure category cannot be null");
    this.diagnosticCode =
        java.util.Objects.requireNonNull(diagnosticCode, "RPC diagnostic code cannot be null");
  }

  Category category() {
    return category;
  }

  String diagnosticCode() {
    return diagnosticCode;
  }
}
