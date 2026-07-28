package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class TenantMembershipSuspensionApiIT {

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
  void shouldSuspendAndPersistActiveTenantMembership() throws Exception {
    Tenant tenant = createTenant("active-tenant", "Active Tenant");

    PlatformUser platformUser = createPlatformUser("active-user");

    TenantMembership membership =
        tenantMembershipRepository.save(TenantMembership.create(tenant.id(), platformUser.id()));

    mockMvc
        .perform(
            post(endpoint(tenant, membership.id().value()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(membership.id().value().toString()))
        .andExpect(jsonPath("$.tenantId").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.platformUserId").value(platformUser.id().value().toString()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    assertEquals("SUSPENDED", persistedStatus(membership.id().value()));
  }

  @Test
  void shouldRejectAlreadySuspendedTenantMembershipWithoutChangingIt() throws Exception {
    Tenant tenant = createTenant("suspended-tenant", "Suspended Tenant");

    PlatformUser platformUser = createPlatformUser("suspended-user");

    TenantMembership membership = TenantMembership.create(tenant.id(), platformUser.id());

    membership.suspend();
    tenantMembershipRepository.save(membership);

    mockMvc
        .perform(
            post(endpoint(tenant, membership.id().value()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-already-suspended"))
        .andExpect(jsonPath("$.title").value("Tenant membership already suspended"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + membership.id().value()
                        + " is already suspended for tenant "
                        + tenant.id().value()))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_ALREADY_SUSPENDED"));

    assertEquals("SUSPENDED", persistedStatus(membership.id().value()));
  }

  @Test
  void shouldReturnNotFoundForUnknownTenant() throws Exception {
    UUID unknownTenantId = UUID.fromString("00000000-0000-0000-0000-000000000010");

    UUID unknownMembershipId = UUID.fromString("00000000-0000-0000-0000-000000000011");

    mockMvc
        .perform(
            post(endpoint(unknownTenantId, unknownMembershipId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Tenant " + unknownTenantId + " was not found"))
        .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
  }

  @Test
  void shouldReturnNotFoundForUnknownTenantMembership() throws Exception {
    Tenant tenant = createTenant("known-tenant", "Known Tenant");

    UUID unknownMembershipId = UUID.fromString("00000000-0000-0000-0000-000000000011");

    mockMvc
        .perform(
            post(endpoint(tenant, unknownMembershipId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant membership not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + unknownMembershipId
                        + " was not found for tenant "
                        + tenant.id().value()))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_NOT_FOUND"));
  }

  @Test
  void shouldHideMembershipBelongingToAnotherTenantWithoutChangingIt() throws Exception {
    Tenant requestedTenant = createTenant("requested-tenant", "Requested Tenant");

    Tenant owningTenant = createTenant("owning-tenant", "Owning Tenant");

    PlatformUser platformUser = createPlatformUser("owning-user");

    TenantMembership membership =
        tenantMembershipRepository.save(
            TenantMembership.create(owningTenant.id(), platformUser.id()));

    mockMvc
        .perform(
            post(endpoint(requestedTenant, membership.id().value()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:tenant-membership-not-found"))
        .andExpect(jsonPath("$.title").value("Tenant membership not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Tenant membership "
                        + membership.id().value()
                        + " was not found for tenant "
                        + requestedTenant.id().value()))
        .andExpect(jsonPath("$.code").value("TENANT_MEMBERSHIP_NOT_FOUND"));

    assertEquals("ACTIVE", persistedStatus(membership.id().value()));

    Long persistedMembershipCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM tenant_memberships
            WHERE id = ? AND tenant_id = ?
            """,
            Long.class,
            membership.id().value(),
            owningTenant.id().value());

    assertEquals(1L, persistedMembershipCount);
  }

  private Tenant createTenant(String key, String name) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of(name)));
  }

  private PlatformUser createPlatformUser(String externalSubject) {
    return platformUserRepository.save(
        PlatformUser.create(ExternalIdentity.of(ISSUER, externalSubject)));
  }

  private String persistedStatus(UUID membershipId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT status
        FROM tenant_memberships
        WHERE id = ?
        """,
        String.class,
        membershipId);
  }

  private static String endpoint(Tenant tenant, UUID membershipId) {
    return endpoint(tenant.id().value(), membershipId);
  }

  private static String endpoint(UUID tenantId, UUID membershipId) {
    return "/api/v1/tenants/" + tenantId + "/memberships/" + membershipId + "/suspension";
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
