package io.github.khaledshawki.eoc.webbff.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

class OidcClientConfigurationTest {
  @Test
  void shouldConfigureConfidentialAuthorizationCodeClientWithPkce() {
    WebBffProperties properties =
        new WebBffProperties(
            new WebBffProperties.Oidc(
                "eoc-web",
                "secret",
                "http://issuer",
                "http://issuer/auth",
                "http://issuer/token",
                "http://issuer/certs",
                "http://issuer/logout",
                "{baseUrl}/login/oauth2/code/{registrationId}"),
            new WebBffProperties.PlatformApi("http://platform"));
    InMemoryClientRegistrationRepository repository =
        (InMemoryClientRegistrationRepository)
            new OidcClientConfiguration().clientRegistrationRepository(properties);
    ClientRegistration registration = repository.findByRegistrationId("eoc-web");

    assertEquals(
        ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
        registration.getClientAuthenticationMethod());
    assertEquals(
        AuthorizationGrantType.AUTHORIZATION_CODE, registration.getAuthorizationGrantType());
    assertTrue(registration.getClientSettings().isRequireProofKey());
    assertEquals(
        "http://issuer/logout",
        registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint"));
  }
}
