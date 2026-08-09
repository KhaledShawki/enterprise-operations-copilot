package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableAllocationReplayConflictException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableInvoiceAllocationCapacityExceededException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableSettlementStateCorruptedException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReceivableSettlementController.class)
public class ReceivableSettlementApiExceptionHandler {

  private static final URI ACCESS_DENIED_TYPE = URI.create("urn:eoc:problem:access-denied");
  private static final URI PAYMENT_NOT_FOUND_TYPE = URI.create("urn:eoc:problem:payment-not-found");
  private static final URI INVOICE_NOT_FOUND_TYPE = URI.create("urn:eoc:problem:invoice-not-found");
  private static final URI ALLOCATION_NOT_FOUND_TYPE =
      URI.create("urn:eoc:problem:receivable-allocation-not-found");
  private static final URI INVALID_REQUEST_TYPE =
      URI.create("urn:eoc:problem:invalid-receivable-settlement-request");
  private static final URI ALLOCATION_REPLAY_CONFLICT_TYPE =
      URI.create("urn:eoc:problem:receivable-allocation-replay-conflict");
  private static final URI INVOICE_CAPACITY_EXCEEDED_TYPE =
      URI.create("urn:eoc:problem:receivable-invoice-allocation-capacity-exceeded");
  private static final URI SETTLEMENT_CONFLICT_TYPE =
      URI.create("urn:eoc:problem:receivable-settlement-conflict");
  private static final URI SETTLEMENT_STATE_CORRUPTED_TYPE =
      URI.create("urn:eoc:problem:receivable-settlement-state-corrupted");

  @ExceptionHandler(OperationsAccessDeniedException.class)
  ProblemDetail handleAccessDenied() {
    return problem(
        HttpStatus.FORBIDDEN,
        ACCESS_DENIED_TYPE,
        "Access denied",
        "ACCESS_DENIED",
        "You do not have permission to access this resource.");
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail handlePaymentNotFound(PaymentNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        PAYMENT_NOT_FOUND_TYPE,
        "Payment not found",
        "PAYMENT_NOT_FOUND",
        exception.getMessage());
  }

  @ExceptionHandler(InvoiceNotFoundException.class)
  ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        INVOICE_NOT_FOUND_TYPE,
        "Invoice not found",
        "INVOICE_NOT_FOUND",
        exception.getMessage());
  }

  @ExceptionHandler(ReceivableAllocationNotFoundException.class)
  ProblemDetail handleAllocationNotFound(ReceivableAllocationNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        ALLOCATION_NOT_FOUND_TYPE,
        "Receivable allocation not found",
        "RECEIVABLE_ALLOCATION_NOT_FOUND",
        exception.getMessage());
  }

  @ExceptionHandler(InvalidReceivableSettlementRequestException.class)
  ProblemDetail handleInvalidRequest(InvalidReceivableSettlementRequestException exception) {
    return invalidRequest(exception.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleUnreadableRequest() {
    return invalidRequest("Receivable settlement request body is invalid.");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleInvalidPathParameter(MethodArgumentTypeMismatchException exception) {
    String parameterName =
        exception.getName() == null || exception.getName().isBlank()
            ? "unknown"
            : exception.getName();
    return invalidRequest("Path parameter '" + parameterName + "' contains an invalid value.");
  }

  @ExceptionHandler(ReceivableAllocationReplayConflictException.class)
  ProblemDetail handleReplayConflict(ReceivableAllocationReplayConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        ALLOCATION_REPLAY_CONFLICT_TYPE,
        "Receivable allocation replay conflict",
        "RECEIVABLE_ALLOCATION_REPLAY_CONFLICT",
        exception.getMessage());
  }

  @ExceptionHandler(ReceivableInvoiceAllocationCapacityExceededException.class)
  ProblemDetail handleInvoiceCapacityExceeded(
      ReceivableInvoiceAllocationCapacityExceededException exception) {
    return problem(
        HttpStatus.CONFLICT,
        INVOICE_CAPACITY_EXCEEDED_TYPE,
        "Receivable Invoice allocation capacity exceeded",
        "RECEIVABLE_INVOICE_ALLOCATION_CAPACITY_EXCEEDED",
        exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleSettlementConflict(IllegalArgumentException exception) {
    return problem(
        HttpStatus.CONFLICT,
        SETTLEMENT_CONFLICT_TYPE,
        "Receivable settlement conflict",
        "RECEIVABLE_SETTLEMENT_CONFLICT",
        exception.getMessage());
  }

  @ExceptionHandler(ReceivableSettlementStateCorruptedException.class)
  ProblemDetail handleCorruptedState() {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        SETTLEMENT_STATE_CORRUPTED_TYPE,
        "Receivable settlement state is inconsistent",
        "RECEIVABLE_SETTLEMENT_STATE_CORRUPTED",
        "Receivable settlement state is inconsistent and cannot be processed.");
  }

  private static ProblemDetail invalidRequest(String detail) {
    return problem(
        HttpStatus.BAD_REQUEST,
        INVALID_REQUEST_TYPE,
        "Invalid receivable settlement request",
        "INVALID_RECEIVABLE_SETTLEMENT_REQUEST",
        detail);
  }

  private static ProblemDetail problem(
      HttpStatus status, URI type, String title, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
