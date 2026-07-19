package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CurrentUserController.class)
@Import({SecurityConfiguration.class, JwtAuthenticatedUserMapper.class})
class CurrentUserControllerTest {

  private static final String CURRENT_USER_ENDPOINT = "/api/v1/me";
  private static final String ACCESS_TOKEN = "current-user-access-token";
  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void shouldReturnCurrentAuthenticatedUser() throws Exception {
    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt());
    mockMvc
        .perform(
            get(CURRENT_USER_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.roles", contains("auditor", "platform-admin")));

    verify(jwtDecoder).decode(ACCESS_TOKEN);
  }

  @Test
  void shouldRejectUnauthenticatedCurrentUserRequest() throws Exception {
    mockMvc
        .perform(get(CURRENT_USER_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue(ACCESS_TOKEN)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(SUBJECT)
        .claim("scope", "profile email")
        .claim("realm_access", Map.of("roles", List.of("platform-admin", "auditor")))
        .build();
  }
}
