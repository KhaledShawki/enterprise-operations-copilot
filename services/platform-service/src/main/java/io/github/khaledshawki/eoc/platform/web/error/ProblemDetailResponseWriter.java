package io.github.khaledshawki.eoc.platform.web.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

public final class ProblemDetailResponseWriter {

  private final JsonMapper jsonMapper;

  public ProblemDetailResponseWriter(JsonMapper jsonMapper) {
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
  }

  public void write(
      HttpServletResponse response, URI type, String title, String detail, String code)
      throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(response.getStatus()), detail);
    problem.setType(type);
    problem.setTitle(title);
    problem.setProperty("code", code);

    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), problem);
  }
}
