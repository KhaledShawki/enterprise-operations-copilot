package io.github.khaledshawki.eoc.platform.analytics.adapter.in.web;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsReadUnavailableException;
import io.github.khaledshawki.eoc.analytics.application.exception.InvalidReceivableQueryException;
import io.github.khaledshawki.eoc.analytics.application.exception.ReceivableNotFoundException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReceivableController.class)
public class ReceivableApiExceptionHandler {

  private static final URI NOT_FOUND_TYPE = URI.create("urn:eoc:problem:receivable-not-found");
  private static final URI INVALID_QUERY_TYPE =
      URI.create("urn:eoc:problem:invalid-receivable-query");
  private static final URI READ_UNAVAILABLE_TYPE =
      URI.create("urn:eoc:problem:analytics-read-unavailable");
  private static final URI PROJECTION_CORRUPTED_TYPE =
      URI.create("urn:eoc:problem:analytics-projection-corrupted");

  @ExceptionHandler(ReceivableNotFoundException.class)
  ProblemDetail handleNotFound(ReceivableNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(NOT_FOUND_TYPE);
    problem.setTitle("Receivable not found");
    problem.setProperty("code", "RECEIVABLE_NOT_FOUND");
    return problem;
  }

  @ExceptionHandler({
    InvalidReceivableQueryException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail handleInvalidQuery(Exception exception) {
    String detail =
        exception instanceof InvalidReceivableQueryException
            ? exception.getMessage()
            : "A receivable query parameter contains an invalid value.";
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(INVALID_QUERY_TYPE);
    problem.setTitle("Invalid receivable query");
    problem.setProperty("code", "INVALID_RECEIVABLE_QUERY");
    return problem;
  }

  @ExceptionHandler(AnalyticsReadUnavailableException.class)
  ProblemDetail handleReadUnavailable() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "Analytics data is temporarily unavailable.");
    problem.setType(READ_UNAVAILABLE_TYPE);
    problem.setTitle("Analytics unavailable");
    problem.setProperty("code", "ANALYTICS_READ_UNAVAILABLE");
    return problem;
  }

  @ExceptionHandler(AnalyticsProjectionStateCorruptedException.class)
  ProblemDetail handleProjectionCorrupted() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Analytics projection state is invalid.");
    problem.setType(PROJECTION_CORRUPTED_TYPE);
    problem.setTitle("Analytics projection invalid");
    problem.setProperty("code", "ANALYTICS_PROJECTION_CORRUPTED");
    return problem;
  }
}
