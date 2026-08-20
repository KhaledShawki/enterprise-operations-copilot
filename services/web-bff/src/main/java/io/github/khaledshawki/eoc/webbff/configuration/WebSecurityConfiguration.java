package io.github.khaledshawki.eoc.webbff.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
class WebSecurityConfiguration {
  @Bean
  HttpSessionCsrfTokenRepository csrfTokenRepository() {
    HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
    repository.setHeaderName("X-CSRF-TOKEN");
    return repository;
  }

  @Bean
  SecurityFilterChain webSecurityFilterChain(
      HttpSecurity http,
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository authorizedClients,
      HttpSessionCsrfTokenRepository csrfTokens)
      throws Exception {
    OidcClientInitiatedLogoutSuccessHandler logout =
        new OidcClientInitiatedLogoutSuccessHandler(registrations);
    logout.setPostLogoutRedirectUri("{baseUrl}/");

    http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/bff/session")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/logout")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2Login(
            login ->
                login.authorizedClientRepository(authorizedClients).defaultSuccessUrl("/", true))
        .oauth2Client(client -> client.authorizedClientRepository(authorizedClients))
        .logout(
            config ->
                config
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(logout)
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("EOC_SESSION"));

    return http.build();
  }
}
