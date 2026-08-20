package io.github.khaledshawki.eoc.webbff.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("eoc.web-bff")
public record WebBffProperties(@Valid Oidc oidc, @Valid PlatformApi platformApi) {
  public record Oidc(
      @NotBlank String clientId,
      @NotBlank String clientSecret,
      @NotBlank String issuerUri,
      @NotBlank String authorizationUri,
      @NotBlank String tokenUri,
      @NotBlank String jwkSetUri,
      @NotBlank String endSessionUri,
      @NotBlank String redirectUri) {}

  public record PlatformApi(@NotBlank String baseUrl) {}
}
