package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvalidInvoiceQueryException;
import io.github.khaledshawki.eoc.operations.application.exception.InvoiceNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InvoiceController.class)
public class InvoiceApiExceptionHandler {

  private static final URI ACCESS_DENIED_TYPE = URI.create("urn:eoc:problem:access-denied");
  private static final URI INVOICE_NOT_FOUND_TYPE = URI.create("urn:eoc:problem:invoice-not-found");
  private static final URI INVALID_INVOICE_QUERY_TYPE =
      URI.create("urn:eoc:problem:invalid-invoice-query");

  @ExceptionHandler(OperationsAccessDeniedException.class)
  ProblemDetail handleAccessDenied() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    problem.setType(ACCESS_DENIED_TYPE);
    problem.setTitle("Access denied");
    problem.setProperty("code", "ACCESS_DENIED");
    return problem;
  }

  @ExceptionHandler(InvoiceNotFoundException.class)
  ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(INVOICE_NOT_FOUND_TYPE);
    problem.setTitle("Invoice not found");
    problem.setProperty("code", "INVOICE_NOT_FOUND");
    return problem;
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleInvalidQueryParameter(MethodArgumentTypeMismatchException exception) {
    String parameterName =
        exception.getName() == null || exception.getName().isBlank()
            ? "unknown"
            : exception.getName();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Query parameter '" + parameterName + "' contains an invalid value.");
    problem.setType(INVALID_INVOICE_QUERY_TYPE);
    problem.setTitle("Invalid Invoice query");
    problem.setProperty("code", "INVALID_INVOICE_QUERY");
    return problem;
  }

  @ExceptionHandler(InvalidInvoiceQueryException.class)
  ProblemDetail handleInvalidInvoiceQuery(InvalidInvoiceQueryException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(INVALID_INVOICE_QUERY_TYPE);
    problem.setTitle("Invalid Invoice query");
    problem.setProperty("code", "INVALID_INVOICE_QUERY");
    return problem;
  }
}
