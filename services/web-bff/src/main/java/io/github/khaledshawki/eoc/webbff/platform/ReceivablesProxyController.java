package io.github.khaledshawki.eoc.webbff.platform;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
class ReceivablesProxyController {
  private static final String RECEIVABLES_PATH = "/api/v1/tenants/{tenantId}/analytics/receivables";

  private final PlatformApiGateway platformApi;

  ReceivablesProxyController(PlatformApiGateway platformApi) {
    this.platformApi = platformApi;
  }

  @GetMapping(RECEIVABLES_PATH)
  ResponseEntity<byte[]> list(
      @PathVariable UUID tenantId, @RequestParam MultiValueMap<String, String> query) {
    return forward(RECEIVABLES_PATH, tenantId, query);
  }

  @GetMapping(RECEIVABLES_PATH + "/summary")
  ResponseEntity<byte[]> summary(
      @PathVariable UUID tenantId, @RequestParam MultiValueMap<String, String> query) {
    return forward(RECEIVABLES_PATH + "/summary", tenantId, query);
  }

  private ResponseEntity<byte[]> forward(
      String pathTemplate, UUID tenantId, MultiValueMap<String, String> query) {
    String path =
        UriComponentsBuilder.fromPath(pathTemplate).buildAndExpand(tenantId).toUriString();
    String target =
        UriComponentsBuilder.fromPath(path).queryParams(query).build().encode().toUriString();

    PlatformApiResponse upstream = platformApi.get(target);
    MediaType contentType =
        upstream.contentType() == null ? MediaType.APPLICATION_JSON : upstream.contentType();
    return ResponseEntity.status(upstream.status()).contentType(contentType).body(upstream.body());
  }
}
