package io.github.khaledshawki.eoc.webbff.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class PlatformApiConfiguration {
  @Bean
  RestClient platformApiRestClient(
      RestClient.Builder builder,
      WebBffProperties properties,
      OAuth2AuthorizedClientManager authorizedClientManager,
      OAuth2AuthorizedClientRepository authorizedClients) {
    OAuth2ClientHttpRequestInterceptor oauth =
        new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
    oauth.setClientRegistrationIdResolver(request -> OidcClientConfiguration.REGISTRATION_ID);
    oauth.setAuthorizationFailureHandler(
        OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClients));

    return builder.baseUrl(properties.platformApi().baseUrl()).requestInterceptor(oauth).build();
  }
}
