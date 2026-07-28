package io.github.khaledshawki.eoc.platform.security.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotActiveException;
import io.github.khaledshawki.eoc.tenantaccess.application.exception.PlatformUserNotFoundException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AccessibleTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ListAccessibleTenantsUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  private static final String CURRENT_USER_TENANTS_ENDPOINT = CURRENT_USER_ENDPOINT + "/tenants";

  private static final String ACCESS_TOKEN = "current-user-access-token";

  private static final String ORDINARY_USER_ACCESS_TOKEN = "ordinary-user-access-token";

  private static final String ISSUER = "http://localhost:8180/realms/eoc";

  private static final String SUBJECT = "user-123";

  private static final UUID PLATFORM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final UUID ALPHA_MEMBERSHIP_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");

  private static final UUID ALPHA_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000201");

  private static final UUID BETA_MEMBERSHIP_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000102");

  private static final UUID BETA_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000202");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private ListAccessibleTenantsUseCase listAccessibleTenantsUseCase;

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
  void shouldReturnAccessibleTenantsForOrdinaryAuthenticatedUser() throws Exception {
    ListAccessibleTenantsQuery query = new ListAccessibleTenantsQuery(ISSUER, SUBJECT);

    ListAccessibleTenantsResult result =
        new ListAccessibleTenantsResult(
            List.of(
                accessibleTenant(ALPHA_MEMBERSHIP_ID, ALPHA_TENANT_ID, "alpha", "Alpha Tenant"),
                accessibleTenant(BETA_MEMBERSHIP_ID, BETA_TENANT_ID, "beta", "Beta Tenant")));

    when(jwtDecoder.decode(ORDINARY_USER_ACCESS_TOKEN)).thenReturn(ordinaryUserJwt());

    when(listAccessibleTenantsUseCase.list(query)).thenReturn(result);

    mockMvc
        .perform(
            get(CURRENT_USER_TENANTS_ENDPOINT)
                .queryParam("issuer", "https://attacker.example/realms/other")
                .queryParam("subject", "other-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ORDINARY_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants.length()").value(2))
        .andExpect(jsonPath("$.tenants[0].membershipId").value(ALPHA_MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenants[0].tenantId").value(ALPHA_TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenants[0].tenantKey").value("alpha"))
        .andExpect(jsonPath("$.tenants[0].displayName").value("Alpha Tenant"))
        .andExpect(jsonPath("$.tenants[1].membershipId").value(BETA_MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenants[1].tenantId").value(BETA_TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenants[1].tenantKey").value("beta"))
        .andExpect(jsonPath("$.tenants[1].displayName").value("Beta Tenant"));

    verify(jwtDecoder).decode(ORDINARY_USER_ACCESS_TOKEN);

    verify(listAccessibleTenantsUseCase).list(query);
  }

  @Test
  void shouldReturnEmptyAccessibleTenantCollection() throws Exception {
    ListAccessibleTenantsQuery query = new ListAccessibleTenantsQuery(ISSUER, SUBJECT);

    when(jwtDecoder.decode(ORDINARY_USER_ACCESS_TOKEN)).thenReturn(ordinaryUserJwt());

    when(listAccessibleTenantsUseCase.list(query))
        .thenReturn(new ListAccessibleTenantsResult(List.of()));

    mockMvc
        .perform(
            get(CURRENT_USER_TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ORDINARY_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.tenants").isArray())
        .andExpect(jsonPath("$.tenants").isEmpty());

    verify(listAccessibleTenantsUseCase).list(query);
  }

  @Test
  void shouldReturnNotFoundWhenCurrentPlatformUserIsNotProvisioned() throws Exception {
    ListAccessibleTenantsQuery query = new ListAccessibleTenantsQuery(ISSUER, SUBJECT);

    when(jwtDecoder.decode(ORDINARY_USER_ACCESS_TOKEN)).thenReturn(ordinaryUserJwt());

    when(listAccessibleTenantsUseCase.list(query))
        .thenThrow(new PlatformUserNotFoundException(ExternalIdentity.of(ISSUER, SUBJECT)));

    mockMvc
        .perform(
            get(CURRENT_USER_TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ORDINARY_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-found"))
        .andExpect(jsonPath("$.title").value("Platform user not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Platform user with external identity was not found"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_FOUND"));

    verify(listAccessibleTenantsUseCase).list(query);
  }

  @Test
  void shouldReturnConflictWhenCurrentPlatformUserIsSuspended() throws Exception {
    ListAccessibleTenantsQuery query = new ListAccessibleTenantsQuery(ISSUER, SUBJECT);

    when(jwtDecoder.decode(ORDINARY_USER_ACCESS_TOKEN)).thenReturn(ordinaryUserJwt());

    when(listAccessibleTenantsUseCase.list(query))
        .thenThrow(new PlatformUserNotActiveException(PlatformUserId.of(PLATFORM_USER_ID)));

    mockMvc
        .perform(
            get(CURRENT_USER_TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ORDINARY_USER_ACCESS_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:platform-user-not-active"))
        .andExpect(jsonPath("$.title").value("Platform user is not active"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail").value("Platform user " + PLATFORM_USER_ID + " is not active"))
        .andExpect(jsonPath("$.code").value("PLATFORM_USER_NOT_ACTIVE"));

    verify(listAccessibleTenantsUseCase).list(query);
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

  @Test
  void shouldRejectUnauthenticatedAccessibleTenantRequest() throws Exception {
    mockMvc
        .perform(get(CURRENT_USER_TENANTS_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(listAccessibleTenantsUseCase);
  }

  private static AccessibleTenantResult accessibleTenant(
      UUID membershipId, UUID tenantId, String tenantKey, String displayName) {
    return new AccessibleTenantResult(
        TenantMembershipId.of(membershipId),
        TenantId.of(tenantId),
        TenantKey.of(tenantKey),
        TenantName.of(displayName));
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

  private static Jwt ordinaryUserJwt() {
    return Jwt.withTokenValue(ORDINARY_USER_ACCESS_TOKEN)
        .header("alg", "RS256")
        .issuer(ISSUER)
        .subject(SUBJECT)
        .claim("scope", "profile")
        .claim("realm_access", Map.of("roles", List.of("auditor")))
        .build();
  }
}
