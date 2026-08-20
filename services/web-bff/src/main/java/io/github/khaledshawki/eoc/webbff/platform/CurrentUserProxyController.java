package io.github.khaledshawki.eoc.webbff.platform;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CurrentUserProxyController {
  private final PlatformApiGateway platformApi;

  CurrentUserProxyController(PlatformApiGateway platformApi) {
    this.platformApi = platformApi;
  }

  @GetMapping("/api/v1/me")
  ResponseEntity<byte[]> currentUser() {
    return response(platformApi.get("/api/v1/me"));
  }

  @GetMapping("/api/v1/me/tenants")
  ResponseEntity<byte[]> tenants() {
    return response(platformApi.get("/api/v1/me/tenants"));
  }

  private static ResponseEntity<byte[]> response(PlatformApiResponse upstream) {
    MediaType contentType =
        upstream.contentType() == null ? MediaType.APPLICATION_JSON : upstream.contentType();
    return ResponseEntity.status(upstream.status()).contentType(contentType).body(upstream.body());
  }
}
