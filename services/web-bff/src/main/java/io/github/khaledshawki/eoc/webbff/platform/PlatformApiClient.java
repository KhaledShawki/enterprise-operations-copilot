package io.github.khaledshawki.eoc.webbff.platform;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class PlatformApiClient implements PlatformApiGateway {
  private final RestClient restClient;

  PlatformApiClient(@Qualifier("platformApiRestClient") RestClient restClient) {
    this.restClient = Objects.requireNonNull(restClient, "Platform RestClient cannot be null");
  }

  @Override
  public PlatformApiResponse get(String path) {
    try {
      PlatformApiResponse response =
          restClient
              .get()
              .uri(path)
              .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON)
              .exchange(
                  (request, upstream) -> {
                    byte[] body = upstream.bodyTo(byte[].class);
                    return new PlatformApiResponse(
                        upstream.getStatusCode(),
                        upstream.getHeaders().getContentType(),
                        body == null ? new byte[0] : body);
                  });
      if (response == null) throw new IllegalStateException("Platform API returned no response");
      return response;
    } catch (RestClientException exception) {
      throw new PlatformApiUnavailableException(exception);
    }
  }
}
