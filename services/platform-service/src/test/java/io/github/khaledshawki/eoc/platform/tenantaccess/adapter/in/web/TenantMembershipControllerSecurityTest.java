package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

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
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantMembershipController.class)
@Import(SecurityConfiguration.class)
class TenantMembershipControllerSecurityTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private static final UUID PLATFORM_USER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  private static final UUID MEMBERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/memberships";
  private static final String MEMBERSHIP_ENDPOINT = ENDPOINT + "/" + MEMBERSHIP_ID;
  private static final String SUSPENSION_ENDPOINT = MEMBERSHIP_ENDPOINT + "/suspension";
  private static final String ACTIVATION_ENDPOINT = MEMBERSHIP_ENDPOINT + "/activation";

  private static final String PLATFORM_ADMIN_TOKEN = "platform-admin-token";

  private static final String VALID_REQUEST =
      """
      {
        "platformUserId": "00000000-0000-0000-0000-000000000002"
      }
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private AssignTenantMembershipUseCase assignTenantMembershipUseCase;
  @MockitoBean private GetTenantMembershipUseCase getTenantMembershipUseCase;
  @MockitoBean private SuspendTenantMembershipUseCase suspendTenantMembershipUseCase;
  @MockitoBean private ActivateTenantMembershipUseCase activateTenantMembershipUseCase;

  @Test
  void shouldRejectUnauthenticatedMembershipAssignment() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
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

    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldRejectMembershipAssignmentWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_profile")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
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

    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldAllowMembershipAssignmentWithPlatformAdminRole() throws Exception {
    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(TENANT_ID, PLATFORM_USER_ID);

    AssignTenantMembershipResult result =
        new AssignTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.ACTIVE);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);
    when(assignTenantMembershipUseCase.assign(command)).thenReturn(result);

    mockMvc
        .perform(
            post(ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
        .andExpect(status().isCreated());

    verify(assignTenantMembershipUseCase).assign(command);
  }

  @Test
  void shouldRejectUnauthenticatedMembershipQuery() throws Exception {
    mockMvc
        .perform(get(MEMBERSHIP_ENDPOINT).accept(MediaType.APPLICATION_JSON))
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldRejectMembershipQueryWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            get(MEMBERSHIP_ENDPOINT)
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldAllowMembershipQueryWithPlatformAdminRole() throws Exception {
    GetTenantMembershipQuery query = new GetTenantMembershipQuery(TENANT_ID, MEMBERSHIP_ID);

    GetTenantMembershipResult result =
        new GetTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.ACTIVE);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);

    when(getTenantMembershipUseCase.get(query)).thenReturn(result);

    mockMvc
        .perform(
            get(MEMBERSHIP_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(getTenantMembershipUseCase).get(query);
    verifyNoInteractions(assignTenantMembershipUseCase);
  }

  @Test
  void shouldRejectUnauthenticatedMembershipSuspension() throws Exception {
    mockMvc
        .perform(post(SUSPENSION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
    verifyNoInteractions(suspendTenantMembershipUseCase);
  }

  @Test
  void shouldRejectMembershipSuspensionWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            post(SUSPENSION_ENDPOINT)
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
    verifyNoInteractions(suspendTenantMembershipUseCase);
  }

  @Test
  void shouldAllowMembershipSuspensionWithPlatformAdminRole() throws Exception {
    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    SuspendTenantMembershipResult result =
        new SuspendTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.SUSPENDED);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);

    when(suspendTenantMembershipUseCase.suspend(command)).thenReturn(result);

    mockMvc
        .perform(
            post(SUSPENSION_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(suspendTenantMembershipUseCase).suspend(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
  }

  @Test
  void shouldRejectUnauthenticatedMembershipActivation() throws Exception {
    mockMvc
        .perform(post(ACTIVATION_ENDPOINT).accept(MediaType.APPLICATION_JSON))
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
    verifyNoInteractions(suspendTenantMembershipUseCase);
    verifyNoInteractions(activateTenantMembershipUseCase);
  }

  @Test
  void shouldRejectMembershipActivationWithoutPlatformAdminRole() throws Exception {
    mockMvc
        .perform(
            post(ACTIVATION_ENDPOINT)
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

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
    verifyNoInteractions(suspendTenantMembershipUseCase);
    verifyNoInteractions(activateTenantMembershipUseCase);
  }

  @Test
  void shouldAllowMembershipActivationWithPlatformAdminRole() throws Exception {
    ActivateTenantMembershipCommand command =
        new ActivateTenantMembershipCommand(TENANT_ID, MEMBERSHIP_ID);

    ActivateTenantMembershipResult result =
        new ActivateTenantMembershipResult(
            TenantMembershipId.of(MEMBERSHIP_ID),
            TenantId.of(TENANT_ID),
            PlatformUserId.of(PLATFORM_USER_ID),
            TenantMembershipStatus.ACTIVE);

    Jwt platformAdminJwt =
        Jwt.withTokenValue(PLATFORM_ADMIN_TOKEN)
            .header("alg", "RS256")
            .issuer("http://localhost:8180/realms/eoc")
            .subject("user-123")
            .claim("realm_access", Map.of("roles", List.of("platform-admin")))
            .build();

    when(jwtDecoder.decode(PLATFORM_ADMIN_TOKEN)).thenReturn(platformAdminJwt);

    when(activateTenantMembershipUseCase.activate(command)).thenReturn(result);

    mockMvc
        .perform(
            post(ACTIVATION_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + PLATFORM_ADMIN_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.platformUserId").value(PLATFORM_USER_ID.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(activateTenantMembershipUseCase).activate(command);

    verifyNoInteractions(assignTenantMembershipUseCase);
    verifyNoInteractions(getTenantMembershipUseCase);
    verifyNoInteractions(suspendTenantMembershipUseCase);
  }
}
