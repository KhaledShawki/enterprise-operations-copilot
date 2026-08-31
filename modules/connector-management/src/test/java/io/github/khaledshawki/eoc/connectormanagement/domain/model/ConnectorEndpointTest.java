package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class ConnectorEndpointTest {

  @Test
  void shouldAcceptTrimmedHttpHttpsAndGrpcEndpoints() {
    assertEquals(
        URI.create("https://erp.example.com/api/v1?company=main"),
        ConnectorEndpoint.of("  https://erp.example.com/api/v1?company=main  ").value());
    assertEquals(
        URI.create("http://localhost:8080"), ConnectorEndpoint.of("http://localhost:8080").value());
    assertEquals(
        URI.create("grpc://mock-erp:9090"), ConnectorEndpoint.of("grpc://mock-erp:9090").value());
    assertEquals(
        URI.create("grpcs://erp.example.com:443"),
        ConnectorEndpoint.of("grpcs://erp.example.com:443").value());
  }

  @Test
  void shouldRejectNullEmptyAndMalformedEndpoints() {
    assertThrows(NullPointerException.class, () -> ConnectorEndpoint.of(null));
    assertThrows(IllegalArgumentException.class, () -> ConnectorEndpoint.of(" "));
    assertThrows(IllegalArgumentException.class, () -> ConnectorEndpoint.of("https://bad host"));
  }

  @Test
  void shouldRejectRelativeOrUnsupportedEndpoints() {
    assertThrows(IllegalArgumentException.class, () -> ConnectorEndpoint.of("/api/v1"));
    assertThrows(
        IllegalArgumentException.class, () -> ConnectorEndpoint.of("ftp://erp.example.com"));
  }

  @Test
  void shouldRejectEndpointsWithoutHostOrWithEmbeddedUserInformation() {
    assertThrows(IllegalArgumentException.class, () -> ConnectorEndpoint.of("https:///api/v1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ConnectorEndpoint.of("https://user:password@erp.example.com/api"));
  }

  @Test
  void shouldRejectEndpointFragmentAndNullUri() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ConnectorEndpoint.of("https://erp.example.com/api#credentials"));
    assertThrows(NullPointerException.class, () -> new ConnectorEndpoint(null));
  }

  @Test
  void shouldRejectOversizedEndpoint() {
    String oversizedPath = "a".repeat(ConnectorEndpoint.MAX_LENGTH);

    assertThrows(
        IllegalArgumentException.class,
        () -> ConnectorEndpoint.of("https://erp.example.com/" + oversizedPath));
  }
}
