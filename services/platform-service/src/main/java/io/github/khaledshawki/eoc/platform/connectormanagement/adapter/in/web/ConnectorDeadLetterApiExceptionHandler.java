package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterMalformedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterOffsetOutOfRangeException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayCollisionException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayLimitExceededException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterReplayNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorDeadLetterUnavailableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConnectorDeadLetterRecoveryController.class)
class ConnectorDeadLetterApiExceptionHandler {

  @ExceptionHandler({
    ConnectorDeadLetterNotFoundException.class,
    ConnectorDeadLetterReplayNotFoundException.class
  })
  ProblemDetail handleNotFound(RuntimeException exception) {
    return problem(
        HttpStatus.NOT_FOUND, "CONNECTOR_DEAD_LETTER_NOT_FOUND", "Resource not found", exception);
  }

  @ExceptionHandler(ConnectorDeadLetterOffsetOutOfRangeException.class)
  ProblemDetail handleOffsetOutOfRange(ConnectorDeadLetterOffsetOutOfRangeException exception) {
    return problem(
        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
        "CONNECTOR_DEAD_LETTER_OFFSET_OUT_OF_RANGE",
        "Dead-letter offset is outside retained range",
        exception);
  }

  @ExceptionHandler(ConnectorDeadLetterMalformedException.class)
  ProblemDetail handleMalformed(ConnectorDeadLetterMalformedException exception) {
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "CONNECTOR_DEAD_LETTER_MALFORMED",
        "Dead-letter record is not replayable",
        exception);
  }

  @ExceptionHandler(ConnectorDeadLetterReplayLimitExceededException.class)
  ProblemDetail handleReplayLimit(ConnectorDeadLetterReplayLimitExceededException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "CONNECTOR_DEAD_LETTER_REPLAY_LIMIT_EXCEEDED",
        "Dead-letter replay limit exceeded",
        exception);
  }

  @ExceptionHandler(ConnectorDeadLetterReplayCollisionException.class)
  ProblemDetail handleReplayCollision(ConnectorDeadLetterReplayCollisionException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "CONNECTOR_DEAD_LETTER_REPLAY_COLLISION",
        "Dead-letter replay coordinate collision",
        exception);
  }

  @ExceptionHandler(ConnectorDeadLetterUnavailableException.class)
  ProblemDetail handleUnavailable(ConnectorDeadLetterUnavailableException exception) {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CONNECTOR_DEAD_LETTER_UNAVAILABLE",
        "Dead-letter transport unavailable",
        exception);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "INVALID_CONNECTOR_DEAD_LETTER_REQUEST",
        "Invalid dead-letter request",
        exception);
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String title, RuntimeException exception) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setType(URI.create("urn:eoc:problem:" + code.toLowerCase().replace('_', '-')));
    problem.setTitle(title);
    problem.setProperty("code", code);
    return problem;
  }
}
