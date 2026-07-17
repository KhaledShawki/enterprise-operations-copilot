package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestcontainersConfiguration.class)
public class TenantCreationApiIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM tenants");
  }

  @Test
  void shouldCreateAndPersistTenant() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "tenantKey": "tenant-key",
                        "displayName": "Tenant Name"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.tenantKey").value("tenant-key"))
        .andExpect(jsonPath("$.displayName").value("Tenant Name"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    Long tenantCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE tenant_key = ?", Long.class, "tenant-key");

    String tenantName =
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM tenants WHERE tenant_key = ?", String.class, "tenant-key");

    assertEquals(1L, tenantCount);
    assertEquals("Tenant Name", tenantName);
  }

  @Test
  void shouldReturnConflictWhenTenantKeyAlreadyExists() throws Exception {
    String firstRequest =
        """
          {
            "tenantKey": "duplicate-tenant-key",
            "displayName": "Tenant Name"
          }
        """;

    String secondRequest =
        """
          {
            "tenantKey": "duplicate-tenant-key",
            "displayName": "Another Tenant Name"
          }
        """;

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(firstRequest))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(secondRequest))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Tenant key already exists"))
        .andExpect(jsonPath("$.detail").value("Tenant key duplicate-tenant-key already exists"))
        .andExpect(jsonPath("$.code").value("TENANT_KEY_ALREADY_EXISTS"))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-key-already-exists"));
  }
}
