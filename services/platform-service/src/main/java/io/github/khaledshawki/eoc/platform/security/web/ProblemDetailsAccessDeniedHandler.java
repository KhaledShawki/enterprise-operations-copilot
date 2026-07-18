package io.github.khaledshawki.eoc.platform.security.web;

import io.github.khaledshawki.eoc.platform.web.error.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {
  private static final URI ACCESS_DENIED_TYPE = URI.create("urn:eoc:problem:access-denied");
  private static final String ACCESS_DENIED_CODE = "ACCESS_DENIED";
  private static final String ACCESS_DENIED_TITLE = "Access denied";
  private static final String ACCESS_DENIED_DETAIL =
      "You do not have permission to access this resource.";

  private final BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler;
  private final ProblemDetailResponseWriter problemDetailResponseWriter;

  public ProblemDetailsAccessDeniedHandler(
      BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler,
      ProblemDetailResponseWriter problemDetailResponseWriter) {
    this.bearerTokenAccessDeniedHandler =
        Objects.requireNonNull(
            bearerTokenAccessDeniedHandler, "BearerTokenAccessDeniedHandler cannot be null");
    this.problemDetailResponseWriter =
        Objects.requireNonNull(
            problemDetailResponseWriter, "ProblemDetailResponseWriter cannot be null");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    bearerTokenAccessDeniedHandler.handle(request, response, accessDeniedException);

    problemDetailResponseWriter.write(
        response,
        ACCESS_DENIED_TYPE,
        ACCESS_DENIED_TITLE,
        ACCESS_DENIED_DETAIL,
        ACCESS_DENIED_CODE);
  }
}
