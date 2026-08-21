package io.github.khaledshawki.eoc.webbff.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

class ReceivablesProxyControllerTest {
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000071");

  @Test
  void shouldForwardOnlyToTheTenantReceivablesBoundaryAndPreserveQueryValues() {
    AtomicReference<String> requestedPath = new AtomicReference<>();
    PlatformApiGateway gateway =
        path -> {
          requestedPath.set(path);
          return new PlatformApiResponse(
              HttpStatus.OK, MediaType.APPLICATION_JSON, "{}".getBytes(StandardCharsets.UTF_8));
        };
    var query = new LinkedMultiValueMap<String, String>();
    query.add("overdue", "true");
    query.add("status", "OPEN");
    query.add("status", "PARTIALLY_PAID");
    query.add("page", "2");

    new ReceivablesProxyController(gateway).list(TENANT_ID, query);

    assertEquals(
        "/api/v1/tenants/00000000-0000-0000-0000-000000000071/analytics/receivables"
            + "?overdue=true&status=OPEN&status=PARTIALLY_PAID&page=2",
        requestedPath.get());
  }

  @Test
  void shouldPreserveSummaryProblemStatusContentTypeAndBody() {
    byte[] body = "{\"code\":\"ACCESS_DENIED\"}".getBytes(StandardCharsets.UTF_8);
    PlatformApiGateway gateway =
        path ->
            new PlatformApiResponse(HttpStatus.FORBIDDEN, MediaType.APPLICATION_PROBLEM_JSON, body);
    var query = new LinkedMultiValueMap<String, String>();
    query.add("businessDate", "2026-08-20");

    ResponseEntity<byte[]> response =
        new ReceivablesProxyController(gateway).summary(TENANT_ID, query);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertArrayEquals(body, response.getBody());
  }
}
