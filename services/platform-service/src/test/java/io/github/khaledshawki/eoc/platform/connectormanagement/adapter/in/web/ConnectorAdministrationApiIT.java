package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConnectorAdministrationApiIT {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "connector-administrator";
  private static final String ACCESS_TOKEN = "connector-access-token";
  private static final UUID CREDENTIAL_REFERENCE =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final String VALID_REQUEST =
      """
      {
        "name": "Primary ERP",
        "type": "mock-erp",
        "endpoint": "https://erp.example.com/api",
        "credentialReference": "00000000-0000-0000-0000-000000000020",
        "syncPolicy": {
          "mode": "MANUAL",
          "interval": "PT0S"
        }
      }
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipRepository tenantMembershipRepository;
  @Autowired private ConnectorRepository connectorRepository;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connectors");
    jdbcTemplate.update("DELETE FROM tenant_memberships");
    jdbcTemplate.update("DELETE FROM platform_users");
    jdbcTemplate.update("DELETE FROM tenants");
  }

  @Test
  void tenantAdministratorShouldManageCompleteConnectorLifecycle() throws Exception {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), "tenant-admin");
    decodeAccessToken(SUBJECT, false);

    MvcResult created =
        mockMvc
            .perform(authenticated(post(connectorsEndpoint(tenant))).content(VALID_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tenantId").value(tenant.id().value().toString()))
            .andExpect(jsonPath("$.name").value("Primary ERP"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();

    String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
    UUID connectorId = connectorId(location);

    mockMvc
        .perform(authenticated(post(connectorsEndpoint(tenant))).content(VALID_REQUEST))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONNECTOR_NAME_ALREADY_EXISTS"));

    mockMvc
        .perform(authenticated(get(connectorsEndpoint(tenant))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectors.length()").value(1))
        .andExpect(jsonPath("$.connectors[0].id").value(connectorId.toString()));

    mockMvc
        .perform(authenticated(get(connectorEndpoint(tenant, connectorId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.credentialReference").value(CREDENTIAL_REFERENCE.toString()))
        .andExpect(jsonPath("$.syncPolicy.mode").value("MANUAL"));

    mockMvc
        .perform(authenticated(post(connectorEndpoint(tenant, connectorId) + "/suspension")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONNECTOR_NOT_ACTIVE"));

    mockMvc
        .perform(authenticated(get(connectorEndpoint(tenant, UUID.randomUUID()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONNECTOR_NOT_FOUND"));

    mockMvc
        .perform(authenticated(post(connectorEndpoint(tenant, connectorId) + "/activation")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(authenticated(post(connectorEndpoint(tenant, connectorId) + "/suspension")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    mockMvc
        .perform(authenticated(post(connectorEndpoint(tenant, connectorId) + "/suspension")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONNECTOR_ALREADY_SUSPENDED"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"operations-manager", "auditor"})
  void readRolesShouldReadButNotAdministerConnectors(String role) throws Exception {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), role);
    Connector connector = connectorRepository.save(connector(tenant));
    decodeAccessToken(SUBJECT, false);

    mockMvc
        .perform(authenticated(get(connectorsEndpoint(tenant))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectors[0].id").value(connector.id().value().toString()));
    mockMvc
        .perform(authenticated(get(connectorEndpoint(tenant, connector.id().value()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(authenticated(post(connectorsEndpoint(tenant))).content(VALID_REQUEST))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc
        .perform(
            authenticated(post(connectorEndpoint(tenant, connector.id().value()) + "/activation")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void unrelatedTenantRoleShouldNotReadOrAdministerConnectors() throws Exception {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), "billing-manager");
    Connector connector = connectorRepository.save(connector(tenant));
    decodeAccessToken(SUBJECT, false);

    mockMvc
        .perform(authenticated(get(connectorEndpoint(tenant, connector.id().value()))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    mockMvc
        .perform(authenticated(post(connectorsEndpoint(tenant))).content(VALID_REQUEST))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void realmRoleAndAnotherTenantMembershipShouldNotBypassTenantScope() throws Exception {
    PlatformUser user = createUser(SUBJECT);
    Tenant allowedTenant = createTenant("alpha");
    Tenant targetTenant = createTenant("beta");
    createMembership(allowedTenant, user, "tenant-admin");
    Connector connector = connectorRepository.save(connector(targetTenant));
    decodeAccessToken(SUBJECT, true);

    MvcResult deniedRead =
        mockMvc
            .perform(authenticated(get(connectorEndpoint(targetTenant, connector.id().value()))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andReturn();
    MvcResult deniedWrite =
        mockMvc
            .perform(authenticated(post(connectorsEndpoint(targetTenant))).content(VALID_REQUEST))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
            .andReturn();

    String readBody = deniedRead.getResponse().getContentAsString();
    String writeBody = deniedWrite.getResponse().getContentAsString();
    org.junit.jupiter.api.Assertions.assertFalse(
        readBody.contains(targetTenant.id().value().toString()));
    org.junit.jupiter.api.Assertions.assertFalse(
        writeBody.contains(targetTenant.id().value().toString()));
  }

  @Test
  void anonymousAccessShouldBeRejectedForEveryConnectorEndpoint() throws Exception {
    UUID tenantId = UUID.randomUUID();
    UUID connectorId = UUID.randomUUID();
    String connectors = "/api/v1/tenants/" + tenantId + "/connectors";
    String connector = connectors + "/" + connectorId;

    for (MockHttpServletRequestBuilder request :
        List.of(
            get(connectors),
            get(connector),
            post(connectors).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST),
            post(connector + "/activation"),
            post(connector + "/suspension"))) {
      mockMvc
          .perform(request)
          .andExpect(status().isUnauthorized())
          .andExpect(
              header()
                  .string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
          .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    verifyNoInteractions(jwtDecoder);
  }

  private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
    return request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
  }

  private void decodeAccessToken(String subject, boolean platformAdmin) {
    if (platformAdmin) {
      when(jwtDecoder.decode(ACCESS_TOKEN))
          .thenReturn(
              Jwt.withTokenValue(ACCESS_TOKEN)
                  .header("alg", "RS256")
                  .issuer(ISSUER)
                  .subject(subject)
                  .claim("realm_access", Map.of("roles", List.of("platform-admin")))
                  .build());
      return;
    }

    when(jwtDecoder.decode(ACCESS_TOKEN))
        .thenReturn(
            Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(subject)
                .build());
  }

  private PlatformUser createUser(String subject) {
    return platformUserRepository.save(PlatformUser.create(ExternalIdentity.of(ISSUER, subject)));
  }

  private Tenant createTenant(String key) {
    return tenantRepository.save(Tenant.create(TenantKey.of(key), TenantName.of("Tenant " + key)));
  }

  private TenantMembership createMembership(Tenant tenant, PlatformUser user, String role) {
    TenantMembership membership = TenantMembership.create(tenant.id(), user.id());
    membership.replaceRoles(Set.of(TenantRoleKey.of(role)));
    return tenantMembershipRepository.save(membership);
  }

  private static Connector connector(Tenant tenant) {
    return Connector.create(
        ConnectorTenantId.of(tenant.id().value()),
        ConnectorName.of("Primary ERP"),
        ConnectorType.of("mock-erp"),
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(CREDENTIAL_REFERENCE),
        SyncPolicy.manual());
  }

  private static String connectorsEndpoint(Tenant tenant) {
    return "/api/v1/tenants/" + tenant.id().value() + "/connectors";
  }

  private static String connectorEndpoint(Tenant tenant, UUID connectorId) {
    return connectorsEndpoint(tenant) + "/" + connectorId;
  }

  private static UUID connectorId(String location) {
    URI uri = URI.create(location);
    String path = uri.getPath();
    return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
  }
}
