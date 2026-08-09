package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

final class InvalidReceivableSettlementRequestException extends RuntimeException {

  InvalidReceivableSettlementRequestException(String detail, RuntimeException cause) {
    super(requireDetail(detail), cause);
  }

  private static String requireDetail(String detail) {
    if (detail == null || detail.isBlank()) {
      return "Receivable settlement request is invalid";
    }
    return detail;
  }
}
