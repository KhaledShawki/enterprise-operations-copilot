package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadyActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.InvalidConnectorConfigurationException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorHealth;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ConnectorController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConnectorControllerTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID CONNECTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID OTHER_CONNECTOR_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000012");
  private static final UUID CREDENTIAL_REFERENCE =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final String ENDPOINT = "/api/v1/tenants/" + TENANT_ID + "/connectors";
  private static final String CONNECTOR_ENDPOINT = ENDPOINT + "/" + CONNECTOR_ID;
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

  @MockitoBean private CreateConnectorUseCase createConnectorUseCase;
  @MockitoBean private ListConnectorsUseCase listConnectorsUseCase;
  @MockitoBean private GetConnectorUseCase getConnectorUseCase;
  @MockitoBean private ActivateConnectorUseCase activateConnectorUseCase;
  @MockitoBean private SuspendConnectorUseCase suspendConnectorUseCase;

  @Test
  void shouldCreateConnector() throws Exception {
    CreateConnectorCommand command = createCommand();
    when(createConnectorUseCase.create(command)).thenReturn(result(CONNECTOR_ID, "Primary ERP"));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(VALID_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "http://localhost" + CONNECTOR_ENDPOINT))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(CONNECTOR_ID.toString()))
        .andExpect(jsonPath("$.tenantId").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.name").value("Primary ERP"))
        .andExpect(jsonPath("$.type").value("mock-erp"))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.endpoint").value("https://erp.example.com/api"))
        .andExpect(jsonPath("$.credentialReference").value(CREDENTIAL_REFERENCE.toString()))
        .andExpect(jsonPath("$.syncPolicy.mode").value("MANUAL"))
        .andExpect(jsonPath("$.syncPolicy.interval").value("PT0S"))
        .andExpect(jsonPath("$.health").value("UNKNOWN"));

    verify(createConnectorUseCase).create(command);
  }

  @Test
  void shouldRejectInvalidCreateRequest() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "",
                      "type": "INVALID TYPE",
                      "endpoint": "",
                      "syncPolicy": {}
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:request-validation-failed"))
        .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(
                    hasItems(
                        "name",
                        "type",
                        "endpoint",
                        "credentialReference",
                        "syncPolicy.mode",
                        "syncPolicy.interval")));

    verifyNoInteractions(createConnectorUseCase);
  }

  @Test
  void shouldListAndGetConnectors() throws Exception {
    ConnectorResult primary = result(CONNECTOR_ID, "Primary ERP");
    ConnectorResult warehouse = result(OTHER_CONNECTOR_ID, "Warehouse ERP");
    when(listConnectorsUseCase.list(new ListConnectorsQuery(TENANT_ID)))
        .thenReturn(new ListConnectorsResult(List.of(primary, warehouse)));
    when(getConnectorUseCase.get(new GetConnectorQuery(TENANT_ID, CONNECTOR_ID)))
        .thenReturn(primary);

    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.connectors.length()").value(2))
        .andExpect(jsonPath("$.connectors[0].id").value(CONNECTOR_ID.toString()))
        .andExpect(jsonPath("$.connectors[1].name").value("Warehouse ERP"));

    mockMvc
        .perform(get(CONNECTOR_ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(CONNECTOR_ID.toString()))
        .andExpect(jsonPath("$.name").value("Primary ERP"));

    verify(listConnectorsUseCase).list(new ListConnectorsQuery(TENANT_ID));
    verify(getConnectorUseCase).get(new GetConnectorQuery(TENANT_ID, CONNECTOR_ID));
  }

  @Test
  void shouldActivateAndSuspendConnector() throws Exception {
    when(activateConnectorUseCase.activate(new ActivateConnectorCommand(TENANT_ID, CONNECTOR_ID)))
        .thenReturn(result(CONNECTOR_ID, "Primary ERP", ConnectorStatus.ACTIVE));
    when(suspendConnectorUseCase.suspend(new SuspendConnectorCommand(TENANT_ID, CONNECTOR_ID)))
        .thenReturn(result(CONNECTOR_ID, "Primary ERP", ConnectorStatus.SUSPENDED));

    mockMvc
        .perform(post(CONNECTOR_ENDPOINT + "/activation").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(post(CONNECTOR_ENDPOINT + "/suspension").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));

    verify(activateConnectorUseCase)
        .activate(new ActivateConnectorCommand(TENANT_ID, CONNECTOR_ID));
    verify(suspendConnectorUseCase).suspend(new SuspendConnectorCommand(TENANT_ID, CONNECTOR_ID));
  }

  @Test
  void shouldReturnConnectorProblemDetails() throws Exception {
    ConnectorTenantId tenantId = ConnectorTenantId.of(TENANT_ID);
    ConnectorId connectorId = ConnectorId.of(CONNECTOR_ID);
    ConnectorName connectorName = ConnectorName.of("Primary ERP");
    when(createConnectorUseCase.create(createCommand()))
        .thenThrow(new ConnectorNameAlreadyExistsException(tenantId, connectorName));
    when(getConnectorUseCase.get(new GetConnectorQuery(TENANT_ID, CONNECTOR_ID)))
        .thenThrow(new ConnectorNotFoundException(tenantId, connectorId));
    when(activateConnectorUseCase.activate(new ActivateConnectorCommand(TENANT_ID, CONNECTOR_ID)))
        .thenThrow(new ConnectorAlreadyActiveException(tenantId, connectorId));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:connector-name-already-exists"))
        .andExpect(jsonPath("$.code").value("CONNECTOR_NAME_ALREADY_EXISTS"));

    mockMvc
        .perform(get(CONNECTOR_ENDPOINT))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:connector-not-found"))
        .andExpect(jsonPath("$.code").value("CONNECTOR_NOT_FOUND"));

    mockMvc
        .perform(post(CONNECTOR_ENDPOINT + "/activation"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:connector-already-active"))
        .andExpect(jsonPath("$.code").value("CONNECTOR_ALREADY_ACTIVE"));
  }

  @Test
  void shouldReturnBadRequestForInvalidConnectorConfiguration() throws Exception {
    when(createConnectorUseCase.create(createCommand()))
        .thenThrow(
            new InvalidConnectorConfigurationException(
                "Manual sync policy interval must be zero", new IllegalArgumentException()));

    mockMvc
        .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:eoc:problem:invalid-connector-configuration"))
        .andExpect(jsonPath("$.code").value("INVALID_CONNECTOR_CONFIGURATION"))
        .andExpect(jsonPath("$.detail").value("Manual sync policy interval must be zero"));
  }

  private static CreateConnectorCommand createCommand() {
    return new CreateConnectorCommand(
        TENANT_ID,
        "Primary ERP",
        "mock-erp",
        "https://erp.example.com/api",
        CREDENTIAL_REFERENCE,
        SyncPolicy.Mode.MANUAL,
        Duration.ZERO);
  }

  private static ConnectorResult result(UUID connectorId, String name) {
    return result(connectorId, name, ConnectorStatus.DRAFT);
  }

  private static ConnectorResult result(UUID connectorId, String name, ConnectorStatus status) {
    return new ConnectorResult(
        ConnectorId.of(connectorId),
        ConnectorTenantId.of(TENANT_ID),
        ConnectorName.of(name),
        ConnectorType.of("mock-erp"),
        status,
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(CREDENTIAL_REFERENCE),
        SyncPolicy.manual(),
        ConnectorHealth.UNKNOWN);
  }
}
