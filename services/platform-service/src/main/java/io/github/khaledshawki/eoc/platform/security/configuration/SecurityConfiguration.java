package io.github.khaledshawki.eoc.platform.security.configuration;

import io.github.khaledshawki.eoc.platform.security.web.ProblemDetailsAccessDeniedHandler;
import io.github.khaledshawki.eoc.platform.security.web.ProblemDetailsAuthenticationEntryPoint;
import io.github.khaledshawki.eoc.platform.web.error.ProblemDetailResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ExpressionJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

  private static final String PLATFORM_ADMIN_ROLE = "platform-admin";
  private static final String REALM_ROLES_EXPRESSION = "[realm_access][roles]";
  private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint,
      ProblemDetailsAccessDeniedHandler accessDeniedHandler) {
    http.csrf(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/tenants",
                        "/api/v1/tenants/*/memberships",
                        "/api/v1/tenants/*/memberships/*/suspension",
                        "/api/v1/tenants/*/memberships/*/activation")
                    .hasRole(PLATFORM_ADMIN_ROLE)
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/tenants/*", "/api/v1/tenants/*/memberships/*")
                    .hasRole(PLATFORM_ADMIN_ROLE)
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    Expression realmRolesExpression =
        new SpelExpressionParser().parseExpression(REALM_ROLES_EXPRESSION);
    ExpressionJwtGrantedAuthoritiesConverter realmRolesConverter =
        new ExpressionJwtGrantedAuthoritiesConverter(realmRolesExpression);
    realmRolesConverter.setAuthorityPrefix(ROLE_AUTHORITY_PREFIX);

    DelegatingJwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new DelegatingJwtGrantedAuthoritiesConverter(
            scopeAuthoritiesConverter, realmRolesConverter);

    JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

    return authenticationConverter;
  }

  @Bean
  BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint() {
    return new BearerTokenAuthenticationEntryPoint();
  }

  @Bean
  ProblemDetailsAuthenticationEntryPoint problemDetailsAuthenticationEntryPoint(
      BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint,
      ProblemDetailResponseWriter problemDetailResponseWriter) {
    return new ProblemDetailsAuthenticationEntryPoint(
        bearerTokenAuthenticationEntryPoint, problemDetailResponseWriter);
  }

  @Bean
  BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler() {
    return new BearerTokenAccessDeniedHandler();
  }

  @Bean
  ProblemDetailsAccessDeniedHandler problemDetailsAccessDeniedHandler(
      BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler,
      ProblemDetailResponseWriter problemDetailResponseWriter) {
    return new ProblemDetailsAccessDeniedHandler(
        bearerTokenAccessDeniedHandler, problemDetailResponseWriter);
  }

  @Bean
  ProblemDetailResponseWriter problemDetailResponseWriter(JsonMapper jsonMapper) {
    return new ProblemDetailResponseWriter(jsonMapper);
  }
}
