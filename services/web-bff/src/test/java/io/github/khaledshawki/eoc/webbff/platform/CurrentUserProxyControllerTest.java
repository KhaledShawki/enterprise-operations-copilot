package io.github.khaledshawki.eoc.webbff.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class CurrentUserProxyControllerTest {
  @Test
  void shouldPreservePlatformProblemStatusContentTypeAndBody() {
    byte[] body = "{\"code\":\"PLATFORM_USER_NOT_FOUND\"}".getBytes(StandardCharsets.UTF_8);
    PlatformApiGateway gateway =
        path ->
            new PlatformApiResponse(HttpStatus.NOT_FOUND, MediaType.APPLICATION_PROBLEM_JSON, body);
    ResponseEntity<byte[]> response = new CurrentUserProxyController(gateway).currentUser();

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
    assertArrayEquals(body, response.getBody());
  }
}
