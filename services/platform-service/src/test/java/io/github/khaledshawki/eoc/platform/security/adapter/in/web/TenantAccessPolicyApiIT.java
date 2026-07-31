package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TenantAccessPolicyApiIT.ProbeConfiguration.class})
class TenantAccessPolicyApiIT {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "user-123";
  private static final String ACCESS_TOKEN = "tenant-access-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipRepository tenantMembershipRepository;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM tenant_memberships");
    jdbcTemplate.update("DELETE FROM platform_users");
    jdbcTemplate.update("DELETE FROM tenants");
  }

  @Test
  void shouldRejectUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(get(endpoint(UUID.randomUUID(), "auditor")).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(jwtDecoder);
  }

  @Test
  void shouldAllowMatchingRoleWithoutMutatingTenantAccessState() throws Exception {
    PlatformUser user = createUser();
    Tenant tenant = createTenant("alpha");
    TenantMembership membership =
        createMembership(tenant, user, Set.of(TenantRoleKey.of("auditor")));
    List<Long> versionsBefore = membershipVersions();

    decodeAccessToken();

    mockMvc
        .perform(authenticatedGet(tenant.id().value(), "auditor"))
        .andExpect(status().isNoContent());

    assertEquals(1L, countRows("platform_users"));
    assertEquals(1L, countRows("tenants"));
    assertEquals(1L, countRows("tenant_memberships"));
    assertEquals(1L, countRows("tenant_membership_roles"));
    assertEquals(versionsBefore, membershipVersions());
    assertEquals("ACTIVE", membershipStatus(membership));
  }

  @Test
  void shouldReturnSameOpaqueDenialForMissingRoleAndAnotherTenant() throws Exception {
    PlatformUser user = createUser();
    Tenant allowedTenant = createTenant("alpha");
    Tenant otherTenant = createTenant("beta");
    createMembership(allowedTenant, user, Set.of(TenantRoleKey.of("auditor")));
    decodeAccessToken();

    MvcResult missingRole = denied(allowedTenant.id().value(), "operations-manager");
    MvcResult otherTenantResult = denied(otherTenant.id().value(), "auditor");

    assertEquals(
        missingRole.getResponse().getContentAsString(),
        otherTenantResult.getResponse().getContentAsString());
    assertFalse(
        otherTenantResult
            .getResponse()
            .getContentAsString()
            .contains(otherTenant.id().value().toString()));
  }

  private MvcResult denied(UUID tenantId, String role) throws Exception {
    return mockMvc
        .perform(authenticatedGet(tenantId, role))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:access-denied"))
        .andExpect(
            jsonPath("$.detail").value("You do not have permission to access this resource."))
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
        .andReturn();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      authenticatedGet(UUID tenantId, String role) {
    return get(endpoint(tenantId, role))
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
        .accept(MediaType.APPLICATION_JSON);
  }

  private void decodeAccessToken() {
    when(jwtDecoder.decode(ACCESS_TOKEN))
        .thenReturn(
            Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(SUBJECT)
                .build());
  }

  private PlatformUser createUser() {
    return platformUserRepository.save(PlatformUser.create(ExternalIdentity.of(ISSUER, SUBJECT)));
  }

  private Tenant createTenant(String key) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key)));
  }

  private TenantMembership createMembership(
      Tenant tenant, PlatformUser user, Set<TenantRoleKey> roles) {
    TenantMembership membership = TenantMembership.create(tenant.id(), user.id());
    membership.replaceRoles(roles);
    return tenantMembershipRepository.save(membership);
  }

  private long countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }

  private List<Long> membershipVersions() {
    return jdbcTemplate.queryForList(
        "SELECT version FROM tenant_memberships ORDER BY id", Long.class);
  }

  private String membershipStatus(TenantMembership membership) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM tenant_memberships WHERE id = ?",
        String.class,
        membership.id().value());
  }

  private static String endpoint(UUID tenantId, String role) {
    return "/test/tenant-access/" + tenantId + "/roles/" + role;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ProbeConfiguration {
    @Bean
    PolicyProbeController policyProbeController() {
      return new PolicyProbeController();
    }
  }

  @RestController
  static class PolicyProbeController {
    @GetMapping("/test/tenant-access/{tenantId}/roles/{requiredRole}")
    @PreAuthorize("@tenantAccessPolicy.hasRole(authentication, #p0, #p1)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@PathVariable UUID tenantId, @PathVariable String requiredRole) {}
  }
}
