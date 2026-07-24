package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformUserProvisioningApiIT {

  private static final String ENDPOINT = "/api/v1/platform-users/me";
  private static final String ACCESS_TOKEN = "platform-user-access-token";
  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM tenant_memberships");
    jdbcTemplate.update("DELETE FROM platform_users");
  }

  @Test
  void shouldProvisionAuthenticatedUserIdempotently() throws Exception {
    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(jwt());

    mockMvc
        .perform(
            put(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost" + ENDPOINT))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    UUID persistedUserId =
        jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM platform_users
            WHERE issuer = ? AND subject = ?
            """,
            UUID.class,
            ISSUER,
            SUBJECT);

    Long countAfterCreation =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM platform_users
            WHERE issuer = ? AND subject = ?
            """,
            Long.class,
            ISSUER,
            SUBJECT);

    String persistedStatus =
        jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM platform_users
            WHERE id = ?
            """,
            String.class,
            persistedUserId);

    assertEquals(1L, countAfterCreation);
    assertEquals("ACTIVE", persistedStatus);

    mockMvc
        .perform(
            put(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(persistedUserId.toString()))
        .andExpect(jsonPath("$.issuer").value(ISSUER))
        .andExpect(jsonPath("$.subject").value(SUBJECT))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    Long countAfterSecondRequest =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM platform_users
            WHERE issuer = ? AND subject = ?
            """,
            Long.class,
            ISSUER,
            SUBJECT);

    assertEquals(1L, countAfterSecondRequest);
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue(ACCESS_TOKEN)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(SUBJECT)
        .claim("scope", "profile email")
        .claim("realm_access", Map.of("roles", List.of("user")))
        .build();
  }
}
