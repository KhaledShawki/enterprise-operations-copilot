package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.InvalidPaymentQueryException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsAccessDeniedException;
import io.github.khaledshawki.eoc.operations.application.exception.PaymentNotFoundException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentApiExceptionHandler {

  private static final URI ACCESS_DENIED_TYPE = URI.create("urn:eoc:problem:access-denied");
  private static final URI PAYMENT_NOT_FOUND_TYPE = URI.create("urn:eoc:problem:payment-not-found");
  private static final URI INVALID_PAYMENT_QUERY_TYPE =
      URI.create("urn:eoc:problem:invalid-payment-query");

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

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail handlePaymentNotFound(PaymentNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(PAYMENT_NOT_FOUND_TYPE);
    problem.setTitle("Payment not found");
    problem.setProperty("code", "PAYMENT_NOT_FOUND");
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
    problem.setType(INVALID_PAYMENT_QUERY_TYPE);
    problem.setTitle("Invalid Payment query");
    problem.setProperty("code", "INVALID_PAYMENT_QUERY");
    return problem;
  }

  @ExceptionHandler(InvalidPaymentQueryException.class)
  ProblemDetail handleInvalidPaymentQuery(InvalidPaymentQueryException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(INVALID_PAYMENT_QUERY_TYPE);
    problem.setTitle("Invalid Payment query");
    problem.setProperty("code", "INVALID_PAYMENT_QUERY");
    return problem;
  }
}
