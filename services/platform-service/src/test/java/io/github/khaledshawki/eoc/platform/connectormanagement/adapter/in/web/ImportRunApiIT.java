package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSourceRegistry;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import io.github.khaledshawki.eoc.platform.connectormanagement.support.TestBusinessDataSources;
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
import java.util.Optional;
import java.util.Set;
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
class ImportRunApiIT {

  private static final String ISSUER = "http://localhost:8180/realms/eoc";
  private static final String SUBJECT = "import-api-operator";
  private static final String ACCESS_TOKEN = "import-api-access-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunLifecycleUseCase importRunLifecycleUseCase;
  @Autowired private PlatformUserRepository platformUserRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipRepository tenantMembershipRepository;
  @MockitoBean private BusinessDataSourceRegistry businessDataSourceRegistry;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          operations_business_partner_import_receipts,
          operations_business_partner_source_mappings,
          operations_business_partner_roles,
          operations_business_partners,
          connector_import_page_acceptances,
          connector_import_checkpoints,
          connector_import_runs,
          connectors,
          tenant_memberships,
          platform_users,
          tenants
        CASCADE
        """);
    when(businessDataSourceRegistry.findByConnectorType(ConnectorType.of("mock-erp")))
        .thenReturn(Optional.of(TestBusinessDataSources.healthy()));
  }

  @Test
  void tenantAdministratorShouldRequestExecuteAndReadImportRun() throws Exception {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), "tenant-admin");
    Connector connector = connectorRepository.save(activeConnector(tenant));
    decodeAccessToken();

    String requestPath =
        "/api/v1/tenants/"
            + tenant.id().value()
            + "/connectors/"
            + connector.id().value()
            + "/import-runs";
    String requestBody = "{\"importType\":\"CUSTOMERS\",\"mode\":\"INCREMENTAL\"}";

    var requested =
        mockMvc
            .perform(authenticated(post(requestPath)).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("REQUESTED"))
            .andExpect(jsonPath("$.statistics.fetched").value(0))
            .andReturn();

    String location = requested.getResponse().getHeader(HttpHeaders.LOCATION);
    org.junit.jupiter.api.Assertions.assertNotNull(location);
    UUID runId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    String runPath = "/api/v1/tenants/" + tenant.id().value() + "/import-runs/" + runId;

    mockMvc
        .perform(authenticated(post(runPath + "/execution")).content("{\"pageSize\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.statistics.fetched").value(3))
        .andExpect(jsonPath("$.statistics.accepted").value(3))
        .andExpect(jsonPath("$.statistics.rejected").value(0));

    mockMvc
        .perform(authenticated(get(runPath)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(runId.toString()))
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void auditorShouldReadButNotRequestOrExecuteImports() throws Exception {
    Tenant tenant = createTenant("alpha");
    createMembership(tenant, createUser(SUBJECT), "auditor");
    Connector connector = connectorRepository.save(activeConnector(tenant));
    UUID runId =
        importRunLifecycleUseCase
            .request(
                new RequestImportRunCommand(
                    tenant.id().value(),
                    connector.id().value(),
                    ImportType.CUSTOMERS,
                    ImportMode.INCREMENTAL))
            .importRunId()
            .value();
    decodeAccessToken();

    String requestPath =
        "/api/v1/tenants/"
            + tenant.id().value()
            + "/connectors/"
            + connector.id().value()
            + "/import-runs";
    String runPath = "/api/v1/tenants/" + tenant.id().value() + "/import-runs/" + runId;

    mockMvc
        .perform(authenticated(get(runPath)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(runId.toString()));

    mockMvc
        .perform(
            authenticated(post(requestPath))
                .content("{\"importType\":\"CUSTOMERS\",\"mode\":\"INCREMENTAL\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

    mockMvc
        .perform(authenticated(post(runPath + "/execution")).content("{\"pageSize\":2}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authenticated(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
    return request
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON);
  }

  private void decodeAccessToken() {
    when(jwtDecoder.decode(ACCESS_TOKEN))
        .thenReturn(
            Jwt.withTokenValue(ACCESS_TOKEN)
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject(SUBJECT)
                .claim("realm_access", Map.of("roles", List.of()))
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

  private static Connector activeConnector(Tenant tenant) {
    Connector connector =
        Connector.create(
            ConnectorTenantId.of(tenant.id().value()),
            ConnectorName.of("Mock ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("grpc://mock-erp:9090"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }
}
