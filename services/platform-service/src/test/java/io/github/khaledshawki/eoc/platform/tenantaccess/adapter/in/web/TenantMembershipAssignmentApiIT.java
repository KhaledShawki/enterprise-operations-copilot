package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembership;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.List;
import java.util.Map;
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
class TenantMembershipAssignmentApiIT {

  private static final String ACCESS_TOKEN = "platform-admin-access-token";
  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "platform-admin-123";

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private PlatformUserRepository platformUserRepository;

  @Autowired private TenantMembershipRepository tenantMembershipRepository;

  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM tenant_memberships");
    jdbcTemplate.update("DELETE FROM platform_users");
    jdbcTemplate.update("DELETE FROM tenants");

    when(jwtDecoder.decode(ACCESS_TOKEN)).thenReturn(platformAdminJwt());
  }

  @Test
  void shouldAssignAndPersistActiveTenantMembership() throws Exception {
    Tenant tenant = createTenant();
    PlatformUser platformUser = createPlatformUser();

    String endpoint = endpoint(tenant);

    mockMvc
        .perform(
            post(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(requestBody(platformUser)))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    org.hamcrest.Matchers.startsWith("http://localhost" + endpoint + "/")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.tenantId").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.platformUserId").value(platformUser.id().value().toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    Long membershipCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM tenant_memberships
            WHERE tenant_id = ? AND platform_user_id = ?
            """,
            Long.class,
            tenant.id().value(),
            platformUser.id().value());

    String persistedStatus =
        jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM tenant_memberships
            WHERE tenant_id = ? AND platform_user_id = ?
            """,
            String.class,
            tenant.id().value(),
            platformUser.id().value());

    assertEquals(1L, membershipCount);
    assertEquals("ACTIVE", persistedStatus);
  }

  @Test
  void shouldReturnConflictWithoutCreatingDuplicateMembership() throws Exception {
    Tenant tenant = createTenant();
    PlatformUser platformUser = createPlatformUser();

    String endpoint = endpoint(tenant);
    String body = requestBody(platformUser);

    mockMvc
        .perform(
            post(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-already-exists"))
        .andExpect(jsonPath("$.title").value("Tenant membership already exists"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Membership already exists for tenant "
                        + tenant.id().value()
                        + " and platform user "
                        + platformUser.id().value()))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_ALREADY_EXISTS"));

    Long membershipCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM tenant_memberships
            WHERE tenant_id = ? AND platform_user_id = ?
            """,
            Long.class,
            tenant.id().value(),
            platformUser.id().value());

    assertEquals(1L, membershipCount);
  }

  @Test
  void shouldNotReactivateExistingSuspendedMembership() throws Exception {
    Tenant tenant = createTenant();
    PlatformUser platformUser = createPlatformUser();

    TenantMembership membership = TenantMembership.create(tenant.id(), platformUser.id());

    membership.suspend();
    tenantMembershipRepository.save(membership);

    mockMvc
        .perform(
            post(endpoint(tenant))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(platformUser)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_ALREADY_EXISTS"));

    String persistedStatus =
        jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM tenant_memberships
            WHERE tenant_id = ? AND platform_user_id = ?
            """,
            String.class,
            tenant.id().value(),
            platformUser.id().value());

    Long membershipCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM tenant_memberships
            WHERE tenant_id = ? AND platform_user_id = ?
            """,
            Long.class,
            tenant.id().value(),
            platformUser.id().value());

    assertEquals("SUSPENDED", persistedStatus);
    assertEquals(1L, membershipCount);
  }

  private Tenant createTenant() {
    return tenantRepository.save(
        Tenant.create(TenantKey.of("tenant-key"), TenantName.of("Tenant Name")));
  }

  private PlatformUser createPlatformUser() {
    return platformUserRepository.save(
        PlatformUser.create(ExternalIdentity.of(ISSUER, "assigned-user-123")));
  }

  private static String endpoint(Tenant tenant) {
    return "/api/v1/tenants/" + tenant.id().value() + "/memberships";
  }

  private static String requestBody(PlatformUser platformUser) {
    return """
        {
          "platformUserId": "%s"
        }
        """
        .formatted(platformUser.id().value());
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
