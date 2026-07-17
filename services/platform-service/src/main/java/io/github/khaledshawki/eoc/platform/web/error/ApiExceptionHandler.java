package io.github.khaledshawki.eoc.platform.web.error;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.TenantKeyAlreadyExistsException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final URI REQUEST_VALIDATION_FAILED_TYPE =
      URI.create("urn:eoc:problem:request-validation-failed");
  private static final URI MALFORMED_REQUEST_TYPE = URI.create("urn:eoc:problem:malformed-request");
  private static final URI TENANT_KEY_ALREADY_EXISTS_TYPE =
      URI.create("urn:eoc:problem:tenant-key-already-exists");

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "One or more request fields are invalid.");
    problem.setType(REQUEST_VALIDATION_FAILED_TYPE);
    problem.setTitle("Request validation failed");
    problem.setProperty("code", "REQUEST_VALIDATION_FAILED");
    problem.setProperty("errors", fieldErrors(exception));

    return problem;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleMalformedRequest() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The request body is missing or contains invalid JSON.");
    problem.setType(MALFORMED_REQUEST_TYPE);
    problem.setTitle("Malformed request");
    problem.setProperty("code", "MALFORMED_REQUEST");
    return problem;
  }

  @ExceptionHandler(TenantKeyAlreadyExistsException.class)
  ProblemDetail handleTenantKeyAlreadyExists(TenantKeyAlreadyExistsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setType(TENANT_KEY_ALREADY_EXISTS_TYPE);
    problem.setTitle("Tenant key already exists");
    problem.setProperty("code", "TENANT_KEY_ALREADY_EXISTS");
    return problem;
  }

  private static List<FieldValidationError> fieldErrors(MethodArgumentNotValidException exception) {
    return exception.getBindingResult().getFieldErrors().stream()
        .map(
            fe ->
                new FieldValidationError(
                    fe.getField(),
                    fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage()))
        .distinct()
        .sorted(
            Comparator.comparing(FieldValidationError::field)
                .thenComparing(FieldValidationError::message))
        .toList();
  }

  public record FieldValidationError(String field, String message) {}
}
