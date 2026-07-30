package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantRoleKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class CurrentUserTenantAccessApiIT {

  private static final String ENDPOINT = "/api/v1/me/tenants";

  private static final String ISSUER = "http://localhost:8180/realms/eoc";

  private static final String CURRENT_USER_SUBJECT = "current-user-123";

  private static final String OTHER_USER_SUBJECT = "other-user-456";

  private static final String CURRENT_USER_ACCESS_TOKEN = "current-user-access-token";

  private static final String UNKNOWN_USER_ACCESS_TOKEN = "unknown-user-access-token";

  private static final String SUSPENDED_USER_ACCESS_TOKEN = "suspended-user-access-token";

  private static final String PLATFORM_ADMIN_ACCESS_TOKEN = "platform-admin-access-token";

  private static final Set<TenantRoleKey> ALPHA_ROLES =
      Set.of(TenantRoleKey.of("tenant-admin"), TenantRoleKey.of("auditor"));

  private static final Set<TenantRoleKey> BETA_ROLES =
      Set.of(TenantRoleKey.of("operations-manager"));

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
  }

  @Test
  void shouldReturnOnlyCurrentUsersActiveAccessibleTenantsWithoutMutation() throws Exception {
    PlatformUser currentUser = createPlatformUser(CURRENT_USER_SUBJECT);

    PlatformUser otherUser = createPlatformUser(OTHER_USER_SUBJECT);

    Tenant betaTenant = createTenant("beta", "Beta Tenant");

    Tenant alphaTenant = createTenant("alpha", "Alpha Tenant");

    Tenant suspendedMembershipTenant =
        createTenant("suspended-membership", "Suspended Membership Tenant");

    Tenant suspendedTenant =
        Tenant.create(TenantKey.of("suspended-tenant"), TenantName.of("Suspended Tenant"));

    suspendedTenant.suspend();

    suspendedTenant = tenantRepository.save(suspendedTenant);

    Tenant otherUsersTenant = createTenant("other-users-tenant", "Other User's Tenant");

    TenantMembership betaMembership = createMembership(betaTenant, currentUser, BETA_ROLES);

    TenantMembership alphaMembership = createMembership(alphaTenant, currentUser, ALPHA_ROLES);

    TenantMembership suspendedMembership =
        TenantMembership.create(suspendedMembershipTenant.id(), currentUser.id());

    suspendedMembership.suspend();

    tenantMembershipRepository.save(suspendedMembership);

    createMembership(suspendedTenant, currentUser);

    createMembership(otherUsersTenant, otherUser);

    List<Long> membershipVersionsBeforeRequest = membershipVersions();

    when(jwtDecoder.decode(CURRENT_USER_ACCESS_TOKEN))
        .thenReturn(jwt(CURRENT_USER_ACCESS_TOKEN, CURRENT_USER_SUBJECT));

    mockMvc
        .perform(
            get(ENDPOINT)
                .queryParam("issuer", "https://attacker.example/realms/other")
                .queryParam("subject", OTHER_USER_SUBJECT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CURRENT_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants.length()").value(2))
        .andExpect(
            jsonPath("$.tenants[0].membershipId").value(alphaMembership.id().value().toString()))
        .andExpect(jsonPath("$.tenants[0].tenantId").value(alphaTenant.id().value().toString()))
        .andExpect(jsonPath("$.tenants[0].tenantKey").value("alpha"))
        .andExpect(jsonPath("$.tenants[0].displayName").value("Alpha Tenant"))
        .andExpect(jsonPath("$.tenants[0].roles.length()").value(2))
        .andExpect(
            jsonPath("$.tenants[0].roles").value(containsInAnyOrder("tenant-admin", "auditor")))
        .andExpect(
            jsonPath("$.tenants[1].membershipId").value(betaMembership.id().value().toString()))
        .andExpect(jsonPath("$.tenants[1].tenantId").value(betaTenant.id().value().toString()))
        .andExpect(jsonPath("$.tenants[1].tenantKey").value("beta"))
        .andExpect(jsonPath("$.tenants[1].displayName").value("Beta Tenant"))
        .andExpect(jsonPath("$.tenants[1].roles.length()").value(1))
        .andExpect(jsonPath("$.tenants[1].roles").value(containsInAnyOrder("operations-manager")));

    assertEquals(5L, countRows("tenant_memberships"));

    assertEquals(3L, countRows("tenant_membership_roles"));

    assertEquals(5L, countRows("tenants"));

    assertEquals(2L, countRows("platform_users"));

    assertEquals("SUSPENDED", membershipStatus(suspendedMembership));

    assertEquals("SUSPENDED", tenantStatus(suspendedTenant));

    assertEquals(membershipVersionsBeforeRequest, membershipVersions());

    assertEquals(0L, countVersionedTenantChanges());

    assertEquals(0L, countVersionedPlatformUserChanges());
  }

  @Test
  void shouldReplaceAndClearMembershipRolesThroughApiAndExposeCurrentState() throws Exception {
    PlatformUser currentUser = createPlatformUser(CURRENT_USER_SUBJECT);

    Tenant tenant = createTenant("alpha", "Alpha Tenant");

    TenantMembership membership = createMembership(tenant, currentUser);

    String rolesEndpoint =
        "/api/v1/tenants/"
            + tenant.id().value()
            + "/memberships/"
            + membership.id().value()
            + "/roles";

    when(jwtDecoder.decode(PLATFORM_ADMIN_ACCESS_TOKEN))
        .thenReturn(
            jwt(PLATFORM_ADMIN_ACCESS_TOKEN, "platform-admin-user", List.of("platform-admin")));

    when(jwtDecoder.decode(CURRENT_USER_ACCESS_TOKEN))
        .thenReturn(jwt(CURRENT_USER_ACCESS_TOKEN, CURRENT_USER_SUBJECT));

    Long initialVersion = membershipVersion(membership);

    mockMvc
        .perform(
            put(rolesEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "roles": [
                        " tenant-admin ",
                        "AUDITOR",
                        "tenant-admin"
                      ]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(membership.id().value().toString()))
        .andExpect(jsonPath("$.tenantId").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.platformUserId").value(currentUser.id().value().toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.roles.length()").value(2))
        .andExpect(jsonPath("$.roles").value(containsInAnyOrder("tenant-admin", "auditor")));

    Long versionAfterReplacement = membershipVersion(membership);

    assertEquals(initialVersion + 1, versionAfterReplacement);

    assertEquals(List.of("auditor", "tenant-admin"), membershipRoleKeys(membership));

    assertEquals("ACTIVE", membershipStatus(membership));

    mockMvc
        .perform(
            get(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CURRENT_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants.length()").value(1))
        .andExpect(jsonPath("$.tenants[0].membershipId").value(membership.id().value().toString()))
        .andExpect(jsonPath("$.tenants[0].tenantId").value(tenant.id().value().toString()))
        .andExpect(jsonPath("$.tenants[0].tenantKey").value("alpha"))
        .andExpect(jsonPath("$.tenants[0].roles.length()").value(2))
        .andExpect(
            jsonPath("$.tenants[0].roles").value(containsInAnyOrder("tenant-admin", "auditor")));

    assertEquals(versionAfterReplacement, membershipVersion(membership));

    mockMvc
        .perform(
            put(rolesEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "roles": []
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(membership.id().value().toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.roles").isEmpty());

    Long versionAfterClear = membershipVersion(membership);

    assertEquals(versionAfterReplacement + 1, versionAfterClear);

    assertEquals(List.of(), membershipRoleKeys(membership));

    assertEquals("ACTIVE", membershipStatus(membership));

    mockMvc
        .perform(
            get(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CURRENT_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants.length()").value(1))
        .andExpect(jsonPath("$.tenants[0].roles").isArray())
        .andExpect(jsonPath("$.tenants[0].roles").isEmpty());

    assertEquals(versionAfterClear, membershipVersion(membership));

    assertEquals(1L, countRows("tenants"));

    assertEquals(1L, countRows("platform_users"));

    assertEquals(1L, countRows("tenant_memberships"));

    assertEquals(0L, countRows("tenant_membership_roles"));
  }

  @Test
  void shouldRejectInvalidMembershipRoleWithoutMutation() throws Exception {
    PlatformUser currentUser = createPlatformUser(CURRENT_USER_SUBJECT);

    Tenant tenant = createTenant("alpha", "Alpha Tenant");

    TenantMembership membership = createMembership(tenant, currentUser);

    String rolesEndpoint =
        "/api/v1/tenants/"
            + tenant.id().value()
            + "/memberships/"
            + membership.id().value()
            + "/roles";

    when(jwtDecoder.decode(PLATFORM_ADMIN_ACCESS_TOKEN))
        .thenReturn(
            jwt(PLATFORM_ADMIN_ACCESS_TOKEN, "platform-admin-user", List.of("platform-admin")));

    Long versionBeforeRequest = membershipVersion(membership);

    mockMvc
        .perform(
            put(rolesEndpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "roles": [
                        "invalid_role"
                      ]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:invalid-tenant-role-key"))
        .andExpect(jsonPath("$.title").value("Invalid tenant role key"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Tenant role key has an invalid format"))
        .andExpect(jsonPath("$.code").value("INVALID_TENANT_ROLE_KEY"));

    assertEquals(versionBeforeRequest, membershipVersion(membership));

    assertEquals(List.of(), membershipRoleKeys(membership));

    assertEquals("ACTIVE", membershipStatus(membership));

    assertEquals(1L, countRows("tenant_memberships"));

    assertEquals(0L, countRows("tenant_membership_roles"));
  }

  @Test
  void shouldReturnEmptyCollectionWhenCurrentUserHasNoMemberships() throws Exception {
    createPlatformUser(CURRENT_USER_SUBJECT);

    when(jwtDecoder.decode(CURRENT_USER_ACCESS_TOKEN))
        .thenReturn(jwt(CURRENT_USER_ACCESS_TOKEN, CURRENT_USER_SUBJECT));

    mockMvc
        .perform(
            get(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + CURRENT_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants").isArray())
        .andExpect(jsonPath("$.tenants").isEmpty());

    assertEquals(1L, countRows("platform_users"));

    assertEquals(0L, countRows("tenant_memberships"));
  }

  @Test
  void shouldReturnNotFoundWhenCurrentPlatformUserIsNotProvisioned() throws Exception {
    when(jwtDecoder.decode(UNKNOWN_USER_ACCESS_TOKEN))
        .thenReturn(jwt(UNKNOWN_USER_ACCESS_TOKEN, "unknown-user"));

    mockMvc
        .perform(
            get(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + UNKNOWN_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-found"))
        .andExpect(jsonPath("$.title").value("Platform user not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Platform user with external identity was not found"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_FOUND"));

    assertEquals(0L, countRows("platform_users"));

    assertEquals(0L, countRows("tenant_memberships"));
  }

  @Test
  void shouldReturnConflictWhenCurrentPlatformUserIsSuspended() throws Exception {
    PlatformUser suspendedUser =
        PlatformUser.create(ExternalIdentity.of(ISSUER, CURRENT_USER_SUBJECT));

    suspendedUser.suspend();

    suspendedUser = platformUserRepository.save(suspendedUser);

    when(jwtDecoder.decode(SUSPENDED_USER_ACCESS_TOKEN))
        .thenReturn(jwt(SUSPENDED_USER_ACCESS_TOKEN, CURRENT_USER_SUBJECT));

    mockMvc
        .perform(
            get(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + SUSPENDED_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-active"))
        .andExpect(jsonPath("$.title").value("Platform user is not active"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value("Platform user " + suspendedUser.id().value() + " is not active"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_ACTIVE"));

    assertEquals("SUSPENDED", platformUserStatus(suspendedUser));

    assertEquals(0L, countVersionedPlatformUserChanges());

    assertEquals(0L, countRows("tenant_memberships"));
  }

  @Test
  void shouldRejectUnauthenticatedRequest() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(jwtDecoder);

    assertEquals(0L, countRows("tenant_memberships"));
  }

  private Tenant createTenant(String key, String name) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of(name)));
  }

  private PlatformUser createPlatformUser(String subject) {
    return platformUserRepository.save(PlatformUser.create(ExternalIdentity.of(ISSUER, subject)));
  }

  private TenantMembership createMembership(Tenant tenant, PlatformUser platformUser) {
    return createMembership(tenant, platformUser, Set.of());
  }

  private TenantMembership createMembership(
      Tenant tenant, PlatformUser platformUser, Set<TenantRoleKey> roles) {
    TenantMembership membership = TenantMembership.create(tenant.id(), platformUser.id());

    membership.replaceRoles(roles);

    return tenantMembershipRepository.save(membership);
  }

  private Long countRows(String tableName) {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
  }

  private String membershipStatus(TenantMembership membership) {
    return jdbcTemplate.queryForObject(
        """
        SELECT status
        FROM tenant_memberships
        WHERE id = ?
        """,
        String.class,
        membership.id().value());
  }

  private String tenantStatus(Tenant tenant) {
    return jdbcTemplate.queryForObject(
        """
        SELECT status
        FROM tenants
        WHERE id = ?
        """,
        String.class,
        tenant.id().value());
  }

  private String platformUserStatus(PlatformUser platformUser) {
    return jdbcTemplate.queryForObject(
        """
        SELECT status
        FROM platform_users
        WHERE id = ?
        """,
        String.class,
        platformUser.id().value());
  }

  private List<Long> membershipVersions() {
    return jdbcTemplate.queryForList(
        """
        SELECT version
        FROM tenant_memberships
        ORDER BY id
        """,
        Long.class);
  }

  private Long membershipVersion(TenantMembership membership) {
    return jdbcTemplate.queryForObject(
        """
        SELECT version
        FROM tenant_memberships
        WHERE id = ?
        """,
        Long.class,
        membership.id().value());
  }

  private List<String> membershipRoleKeys(TenantMembership membership) {
    return jdbcTemplate.queryForList(
        """
        SELECT role_key
        FROM tenant_membership_roles
        WHERE tenant_membership_id = ?
        ORDER BY role_key
        """,
        String.class,
        membership.id().value());
  }

  private Long countVersionedTenantChanges() {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM tenants
        WHERE version <> 0
        """,
        Long.class);
  }

  private Long countVersionedPlatformUserChanges() {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM platform_users
        WHERE version <> 0
        """,
        Long.class);
  }

  private static Jwt jwt(String accessToken, String subject) {
    return jwt(accessToken, subject, List.of("auditor"));
  }

  private static Jwt jwt(String accessToken, String subject, List<String> roles) {
    return Jwt.withTokenValue(accessToken)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(subject)
        .claim("scope", "profile")
        .claim("realm_access", Map.of("roles", roles))
        .build();
  }
}
