package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
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
class TenantQueryApiIT {

  private static final String ACCESS_TOKEN = "platform-admin-access-token";

  private static final String ISSUER = "http://localhost:8180/realms/eoc";

  private static final String SUBJECT = "platform-admin-123";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TenantRepository tenantRepository;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM tenant_memberships");

    jdbcTemplate.update("DELETE FROM platform_users");

    jdbcTemplate.update("DELETE FROM tenants");

    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(platformAdminJwt());
  }

  @Test
  void shouldGetPersistedActiveTenantWithoutChangingIt() throws Exception {
    Tenant tenant = createActiveTenant("active-tenant", "Active Tenant");

    mockMvc
        .perform(
            get(endpoint(tenant.id().value()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.tenantKey").value("active-tenant"))
        .andExpect(jsonPath("$.displayName").value("Active Tenant"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    assertEquals("ACTIVE", persistedStatus(tenant.id().value()));

    assertEquals(1L, persistedTenantCount(tenant.id().value()));
  }

  @Test
  void shouldGetPersistedSuspendedTenantWithoutChangingIt() throws Exception {
    Tenant tenant = createSuspendedTenant("suspended-tenant", "Suspended Tenant");

    mockMvc
        .perform(
            get(endpoint(tenant.id().value()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.tenantKey").value("suspended-tenant"))
        .andExpect(jsonPath("$.displayName").value("Suspended Tenant"))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    assertEquals("SUSPENDED", persistedStatus(tenant.id().value()));

    assertEquals(1L, persistedTenantCount(tenant.id().value()));
  }

  @Test
  void shouldReturnNotFoundForUnknownTenant() throws Exception {
    UUID unknownTenantId = UUID.fromString("00000000-0000-0000-0000-000000000010");

    mockMvc
        .perform(
            get(endpoint(unknownTenantId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + unknownTenantId + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));

    assertEquals(0L, persistedTenantCount(unknownTenantId));
  }

  private Tenant createActiveTenant(String key, String name) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of(name)));
  }

  private Tenant createSuspendedTenant(String key, String name) {
    return tenantRepository.save(
        Tenant.reconstitute(
            TenantId.generate(), TenantKey.of(key), TenantName.of(name), TenantStatus.SUSPENDED));
  }

  private String persistedStatus(UUID tenantId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT status
        FROM tenants
        WHERE id = ?
        """,
        String.class,
        tenantId);
  }

  private Long persistedTenantCount(UUID tenantId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM tenants
        WHERE id = ?
        """,
        Long.class,
        tenantId);
  }

  private static String endpoint(UUID tenantId) {
    return "/api/v1/tenants/" + tenantId;
  }

  private static Jwt platformAdminJwt() {
    return Jwt.withTokenValue(ACCESS_TOKEN)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(SUBJECT)
        .claim("realm_access", Map.of("roles", List.of("platform-admin")))
        .build();
  }
}
