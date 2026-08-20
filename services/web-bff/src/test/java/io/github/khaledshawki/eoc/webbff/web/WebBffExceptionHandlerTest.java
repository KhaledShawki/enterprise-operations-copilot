package io.github.khaledshawki.eoc.webbff.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class WebBffExceptionHandlerTest {
  @Test
  void shouldReturnStableProblemForPlatformTransportFailure() {
    ProblemDetail problem = new WebBffExceptionHandler().handlePlatformUnavailable();
    assertEquals(HttpStatus.BAD_GATEWAY.value(), problem.getStatus());
    assertEquals("BFF_UPSTREAM_UNAVAILABLE", problem.getProperties().get("code"));
  }
}
