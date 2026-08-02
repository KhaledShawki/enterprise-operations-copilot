package io.github.khaledshawki.eoc.platform.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PlatformHealthApiIT {

  private static final String LIVENESS_ENDPOINT = "/actuator/health/liveness";

  private static final String READINESS_ENDPOINT = "/actuator/health/readiness";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Environment environment;

  @Test
  void shouldExposeLivenessWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get(LIVENESS_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void shouldExposeReadinessWhenPostgresAndMigrationsAreReady() throws Exception {
    mockMvc
        .perform(get(READINESS_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("UP"));

    List<String> successfulMigrationVersions =
        jdbcTemplate.queryForList(
            """
            SELECT version
            FROM flyway_schema_history
            WHERE version IN ('1', '2', '3', '4', '5', '6')
              AND success = TRUE
            ORDER BY installed_rank
            """,
            String.class);

    assertEquals(List.of("1", "2", "3", "4", "5", "6"), successfulMigrationVersions);
    assertEquals("validate", environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
  }

  @Test
  void shouldKeepBusinessApisProtected() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  void shouldRequireAuthenticationForAllOtherActuatorPaths() throws Exception {
    mockMvc
        .perform(get("/actuator/health").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    mockMvc
        .perform(get("/actuator/health/liveness/details").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    mockMvc
        .perform(get("/actuator").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }
}
