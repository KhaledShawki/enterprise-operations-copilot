package io.github.khaledshawki.eoc.webbff.web;

import io.github.khaledshawki.eoc.webbff.platform.PlatformApiUnavailableException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class WebBffExceptionHandler {
  @ExceptionHandler(PlatformApiUnavailableException.class)
  ProblemDetail handlePlatformUnavailable() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY, "The platform API is temporarily unavailable.");
    problem.setType(URI.create("urn:eoc:bff:problem:platform-api-unavailable"));
    problem.setTitle("Platform API unavailable");
    problem.setProperty("code", "BFF_UPSTREAM_UNAVAILABLE");
    return problem;
  }
}
