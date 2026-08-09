package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.ReceivableReconciliationStateCorruptedException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReceivableReconciliationController.class)
public class ReceivableReconciliationApiExceptionHandler {

  private static final URI ACCESS_DENIED_TYPE = URI.create("urn:eoc:problem:access-denied");
  private static final URI INVOICE_NOT_FOUND_TYPE = URI.create("urn:eoc:problem:invoice-not-found");
  private static final URI INVALID_REQUEST_TYPE =
      URI.create("urn:eoc:problem:invalid-receivable-reconciliation-request");
  private static final URI STATE_CORRUPTED_TYPE =
      URI.create("urn:eoc:problem:receivable-reconciliation-state-corrupted");

  @ExceptionHandler(OperationsAccessDeniedException.class)
  ProblemDetail handleAccessDenied() {
    return problem(
        HttpStatus.FORBIDDEN,
        ACCESS_DENIED_TYPE,
        "Access denied",
        "ACCESS_DENIED",
        "You do not have permission to access this resource.");
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

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleInvalidPathParameter(MethodArgumentTypeMismatchException exception) {
    String parameterName =
        exception.getName() == null || exception.getName().isBlank()
            ? "unknown"
            : exception.getName();
    return problem(
        HttpStatus.BAD_REQUEST,
        INVALID_REQUEST_TYPE,
        "Invalid receivable reconciliation request",
        "INVALID_RECEIVABLE_RECONCILIATION_REQUEST",
        "Path parameter '" + parameterName + "' contains an invalid value.");
  }

  @ExceptionHandler(ReceivableReconciliationStateCorruptedException.class)
  ProblemDetail handleCorruptedState() {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        STATE_CORRUPTED_TYPE,
        "Receivable reconciliation state is inconsistent",
        "RECEIVABLE_RECONCILIATION_STATE_CORRUPTED",
        "Receivable reconciliation state is inconsistent and cannot be processed.");
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
