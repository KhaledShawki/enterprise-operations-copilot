package io.github.khaledshawki.eoc.platform.operations.adapter.in.web;

import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxEventNotFoundException;
import io.github.khaledshawki.eoc.operations.application.exception.OperationsOutboxRecoveryConflictException;
import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OperationsOutboxAdministrationController.class)
public class OperationsOutboxAdministrationApiExceptionHandler {

  private static final URI NOT_FOUND_TYPE =
      URI.create("urn:eoc:problem:operations-outbox-event-not-found");
  private static final URI RECOVERY_CONFLICT_TYPE =
      URI.create("urn:eoc:problem:operations-outbox-recovery-conflict");
  private static final URI INVALID_REQUEST_TYPE =
      URI.create("urn:eoc:problem:invalid-operations-outbox-request");

  @ExceptionHandler(OperationsOutboxEventNotFoundException.class)
  ProblemDetail handleNotFound(OperationsOutboxEventNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        NOT_FOUND_TYPE,
        "Operations outbox event not found",
        "OPERATIONS_OUTBOX_EVENT_NOT_FOUND",
        exception.getMessage());
  }

  @ExceptionHandler(OperationsOutboxRecoveryConflictException.class)
  ProblemDetail handleRecoveryConflict(OperationsOutboxRecoveryConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        RECOVERY_CONFLICT_TYPE,
        "Operations outbox recovery conflict",
        "OPERATIONS_OUTBOX_RECOVERY_CONFLICT",
        exception.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    MethodArgumentNotValidException.class
  })
  ProblemDetail handleInvalidRequest(Exception exception) {
    return problem(
        HttpStatus.BAD_REQUEST,
        INVALID_REQUEST_TYPE,
        "Invalid Operations outbox request",
        "INVALID_OPERATIONS_OUTBOX_REQUEST",
        "The Operations outbox request contains invalid parameters or content.");
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
