package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.platform.security.configuration.SecurityConfiguration;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantKey;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantController.class)
@Import(SecurityConfiguration.class)
class TenantControllerSecurityTest {

  private static final String TENANTS_ENDPOINT = "/api/v1/tenants";
  private static final String PLATFORM_ADMIN_TOKEN = "platform-admin-token";
  private static final UUID TENANT_ID = UUID.fromString("f6df18c0-306e-4be0-b2c2-b985e3aadcb7");
  private static final String TOKEN_WITHOUT_REALM_ROLES = "token-without-realm-roles";

  private static final String INVALID_TOKEN = "invalid-token";

  private static final String VALID_CREATE_TENANT_REQUEST =
      """
      {
        "tenantKey": "tenant-key",
        "displayName": "Tenant Name"
      }
      """;

  private static final String TENANT_ENDPOINT = TENANTS_ENDPOINT + "/" + TENANT_ID;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private CreateTenantUseCase createTenantUseCase;
  @MockitoBean private GetTenantUseCase getTenantUseCase;

  @Test
  void shouldRejectUnauthenticatedTenantCreation() throws Exception {
    mockMvc
        .perform(
            post(TENANTS_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_TENANT_REQUEST))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(
            jsonPath("$.detail").value("Authentication is required to access this resource."))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldRejectTenantCreationWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            post(TENANTS_ENDPOINT)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_TENANT_REQUEST))
        .andExpect(status().isForbidden())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:access-denied"))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(
            jsonPath("$.detail").value("You do not have permission to access this resource."))
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldAllowTenantCreationWithPlatformAdminRole() throws Exception {
    CreateTenantCommand createTenantCommand = new CreateTenantCommand("tenant-key", "Tenant Name");
    CreateTenantResult result =
        new CreateTenantResult(
            TenantId.of(TENANT_ID),
            TenantKey.of("tenant-key"),
            TenantName.of("Tenant Name"),
            TenantStatus.ACTIVE);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);
    when(createTenantUseCase.create(createTenantCommand)).thenReturn(result);

    mockMvc
        .perform(
            post(TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_TENANT_REQUEST))
        .andExpect(status().isCreated());

    verify(createTenantUseCase).create(createTenantCommand);
  }

  @Test
  void shouldRejectTenantCreationWhenRealmRolesClaimIsMissing() throws Exception {
    Jwt jwtWithoutRealmRoles =
        Jwt.withTokenValue(TOKEN_WITHOUT_REALM_ROLES)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("scope", "profile")
            .build();

    when(jwtDecoder.decode(TOKEN_WITHOUT_REALM_ROLES)).thenReturn(jwtWithoutRealmRoles);

    mockMvc
        .perform(
            post(TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_WITHOUT_REALM_ROLES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_TENANT_REQUEST))
        .andExpect(status().isForbidden());

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldRejectInvalidBearerToken() throws Exception {
    when(jwtDecoder.decode(INVALID_TOKEN)).thenThrow(new BadJwtException("Invalid bearer token"));

    mockMvc
        .perform(
            post(TENANTS_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + INVALID_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE_TENANT_REQUEST))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"invalid_token\"")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(
            jsonPath("$.detail").value("Authentication is required to access this resource."))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(createTenantUseCase);
  }

  @Test
  void shouldIgnoreBearerTokensInQueryParameters() throws Exception {
    mockMvc
        .perform(get(TENANTS_ENDPOINT).queryParam("access_token", "query-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(
            jsonPath("$.detail").value("Authentication is required to access this resource."))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(jwtDecoder, createTenantUseCase);
  }

  @Test
  void shouldRejectUnauthenticatedTenantQuery() throws Exception {
    mockMvc
        .perform(get(TENANT_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:authentication-required"))
        .andExpect(jsonPath("$.title").value("Authentication required"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(
            jsonPath("$.detail").value("Authentication is required to access this resource."))
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    verifyNoInteractions(createTenantUseCase, getTenantUseCase);
  }

  @Test
  void shouldRejectTenantQueryWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            get(TENANT_ENDPOINT)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(
            header()
                .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:access-denied"))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(
            jsonPath("$.detail").value("You do not have permission to access this resource."))
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    verifyNoInteractions(createTenantUseCase, getTenantUseCase);
  }

  @Test
  void shouldAllowTenantQueryWithPlatformAdminRole() throws Exception {
    GetTenantQuery query = new GetTenantQuery(TENANT_ID);

    GetTenantResult result =
        new GetTenantResult(
            TenantId.of(TENANT_ID),
            TenantKey.of("tenant-key"),
            TenantName.of("Tenant Name"),
            TenantStatus.ACTIVE);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);

    when(getTenantUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(
            get(TENANT_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.tenantKey").value("tenant-key"))
        .andExpect(jsonPath("$.displayName").value("Tenant Name"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(getTenantUseCase).get(query);

    verifyNoInteractions(createTenantUseCase);
  }
}
