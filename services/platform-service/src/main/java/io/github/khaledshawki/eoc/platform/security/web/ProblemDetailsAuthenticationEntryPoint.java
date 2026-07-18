package io.github.khaledshawki.eoc.platform.security.web;

import io.github.khaledshawki.eoc.platform.web.error.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

public final class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final URI AUTHENTICATION_REQUIRED_TYPE =
      URI.create("urn:eoc:problem:authentication-required");
  private static final String AUTHENTICATION_REQUIRED_CODE = "AUTHENTICATION_REQUIRED";
  private static final String AUTHENTICATION_REQUIRED_TITLE = "Authentication required";
  private static final String AUTHENTICATION_REQUIRED_DETAIL =
      "Authentication is required to access this resource.";

  private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint;
  private final ProblemDetailResponseWriter problemDetailResponseWriter;

  public ProblemDetailsAuthenticationEntryPoint(
      BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint,
      ProblemDetailResponseWriter problemDetailResponseWriter) {
    this.bearerTokenAuthenticationEntryPoint =
        Objects.requireNonNull(
            bearerTokenAuthenticationEntryPoint,
            "BearerTokenAuthenticationEntryPoint cannot be null");
    this.problemDetailResponseWriter =
        Objects.requireNonNull(
            problemDetailResponseWriter, "ProblemDetailResponseWriter cannot be null");
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authenticationException)
      throws IOException {
    bearerTokenAuthenticationEntryPoint.commence(request, response, authenticationException);

    problemDetailResponseWriter.write(
        response,
        AUTHENTICATION_REQUIRED_TYPE,
        AUTHENTICATION_REQUIRED_TITLE,
        AUTHENTICATION_REQUIRED_DETAIL,
        AUTHENTICATION_REQUIRED_CODE);
  }
}
