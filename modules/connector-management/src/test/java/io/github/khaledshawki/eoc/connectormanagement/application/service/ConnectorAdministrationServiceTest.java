package io.github.khaledshawki.eoc.connectormanagement.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadyActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorAlreadySuspendedException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotActiveException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNotFoundException;
import io.github.khaledshawki.eoc.connectormanagement.application.exception.InvalidConnectorConfigurationException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConnectorResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsQuery;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConnectorAdministrationServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID CREDENTIAL_REFERENCE =
      UUID.fromString("00000000-0000-0000-0000-000000000020");

  private InMemoryConnectorRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryConnectorRepository();
  }

  @Test
  void shouldCreateDraftConnectorFromValidatedConfiguration() {
    CreateConnectorService service = new CreateConnectorService(repository);

    ConnectorResult result = service.create(createCommand(TENANT_ID, " Primary ERP "));

    assertEquals(TENANT_ID, result.tenantId().value());
    assertEquals("Primary ERP", result.name().value());
    assertEquals("mock-erp", result.type().value());
    assertEquals(ConnectorStatus.DRAFT, result.status());
    assertEquals(SyncPolicy.manual(), result.syncPolicy());
    assertEquals(result, ConnectorResult.from(repository.saved(result.connectorId())));
  }

  @Test
  void shouldRejectDuplicateNameBeforeSaving() {
    CreateConnectorService service = new CreateConnectorService(repository);
    service.create(createCommand(TENANT_ID, "Primary ERP"));

    assertThrows(
        ConnectorNameAlreadyExistsException.class,
        () -> service.create(createCommand(TENANT_ID, "Primary ERP")));
    assertEquals(1, repository.size());

    service.create(createCommand(OTHER_TENANT_ID, "Primary ERP"));
    assertEquals(2, repository.size());
  }

  @Test
  void shouldTranslateInvalidDomainInputIntoApplicationException() {
    CreateConnectorService service = new CreateConnectorService(repository);
    CreateConnectorCommand command =
        new CreateConnectorCommand(
            TENANT_ID,
            "Primary ERP",
            "mock-erp",
            "https://erp.example.com/api",
            CREDENTIAL_REFERENCE,
            SyncPolicy.Mode.MANUAL,
            Duration.ofMinutes(5));

    InvalidConnectorConfigurationException exception =
        assertThrows(InvalidConnectorConfigurationException.class, () -> service.create(command));

    assertEquals("Manual sync policy interval must be zero", exception.getMessage());
    assertEquals(0, repository.size());
  }

  @Test
  void shouldGetConnectorOnlyWithinRequestedTenant() {
    Connector connector = repository.save(connector(TENANT_ID, "Primary ERP"));
    GetConnectorService service = new GetConnectorService(repository);

    ConnectorResult result = service.get(new GetConnectorQuery(TENANT_ID, connector.id().value()));

    assertEquals(connector.id(), result.connectorId());
    assertThrows(
        ConnectorNotFoundException.class,
        () -> service.get(new GetConnectorQuery(OTHER_TENANT_ID, connector.id().value())));
  }

  @Test
  void shouldListOnlyConnectorsOwnedByRequestedTenant() {
    repository.save(connector(TENANT_ID, "Warehouse ERP"));
    repository.save(connector(TENANT_ID, "Primary ERP"));
    repository.save(connector(OTHER_TENANT_ID, "Other ERP"));
    ListConnectorsService service = new ListConnectorsService(repository);

    ListConnectorsResult result = service.list(new ListConnectorsQuery(TENANT_ID));

    assertEquals(List.of("Primary ERP", "Warehouse ERP"), names(result));
    assertThrows(
        UnsupportedOperationException.class,
        () -> result.connectors().add(result.connectors().getFirst()));
  }

  @Test
  void shouldActivateDraftAndSuspendedConnectors() {
    Connector connector = repository.save(connector(TENANT_ID, "Primary ERP"));
    ActivateConnectorService service = new ActivateConnectorService(repository);

    ConnectorResult active =
        service.activate(new ActivateConnectorCommand(TENANT_ID, connector.id().value()));
    assertEquals(ConnectorStatus.ACTIVE, active.status());

    connector.suspend();
    repository.save(connector);
    ConnectorResult reactivated =
        service.activate(new ActivateConnectorCommand(TENANT_ID, connector.id().value()));
    assertEquals(ConnectorStatus.ACTIVE, reactivated.status());
  }

  @Test
  void shouldRejectActivatingActiveConnector() {
    Connector connector = connector(TENANT_ID, "Primary ERP");
    connector.activate();
    repository.save(connector);

    assertThrows(
        ConnectorAlreadyActiveException.class,
        () ->
            new ActivateConnectorService(repository)
                .activate(new ActivateConnectorCommand(TENANT_ID, connector.id().value())));
  }

  @Test
  void shouldSuspendActiveConnector() {
    Connector connector = connector(TENANT_ID, "Primary ERP");
    connector.activate();
    repository.save(connector);

    ConnectorResult result =
        new SuspendConnectorService(repository)
            .suspend(new SuspendConnectorCommand(TENANT_ID, connector.id().value()));

    assertEquals(ConnectorStatus.SUSPENDED, result.status());
  }

  @Test
  void shouldRejectSuspendingDraftOrSuspendedConnector() {
    Connector draft = repository.save(connector(TENANT_ID, "Draft ERP"));
    SuspendConnectorService service = new SuspendConnectorService(repository);

    assertThrows(
        ConnectorNotActiveException.class,
        () -> service.suspend(new SuspendConnectorCommand(TENANT_ID, draft.id().value())));

    Connector suspended = connector(TENANT_ID, "Suspended ERP");
    suspended.activate();
    suspended.suspend();
    repository.save(suspended);

    assertThrows(
        ConnectorAlreadySuspendedException.class,
        () -> service.suspend(new SuspendConnectorCommand(TENANT_ID, suspended.id().value())));
  }

  @Test
  void shouldRejectUnknownConnectorForBothLifecycleOperations() {
    UUID connectorId = UUID.randomUUID();

    assertThrows(
        ConnectorNotFoundException.class,
        () ->
            new ActivateConnectorService(repository)
                .activate(new ActivateConnectorCommand(TENANT_ID, connectorId)));
    assertThrows(
        ConnectorNotFoundException.class,
        () ->
            new SuspendConnectorService(repository)
                .suspend(new SuspendConnectorCommand(TENANT_ID, connectorId)));
  }

  @Test
  void shouldRejectNullDependenciesAndInputs() {
    assertThrows(NullPointerException.class, () -> new CreateConnectorService(null));
    assertThrows(NullPointerException.class, () -> new GetConnectorService(null));
    assertThrows(NullPointerException.class, () -> new ListConnectorsService(null));
    assertThrows(NullPointerException.class, () -> new ActivateConnectorService(null));
    assertThrows(NullPointerException.class, () -> new SuspendConnectorService(null));

    assertThrows(
        NullPointerException.class, () -> new CreateConnectorService(repository).create(null));
    assertThrows(NullPointerException.class, () -> new GetConnectorService(repository).get(null));
    assertThrows(
        NullPointerException.class, () -> new ListConnectorsService(repository).list(null));
    assertThrows(
        NullPointerException.class, () -> new ActivateConnectorService(repository).activate(null));
    assertThrows(
        NullPointerException.class, () -> new SuspendConnectorService(repository).suspend(null));
  }

  private static CreateConnectorCommand createCommand(UUID tenantId, String name) {
    return new CreateConnectorCommand(
        tenantId,
        name,
        "mock-erp",
        "https://erp.example.com/api",
        CREDENTIAL_REFERENCE,
        SyncPolicy.Mode.MANUAL,
        Duration.ZERO);
  }

  private static Connector connector(UUID tenantId, String name) {
    return Connector.create(
        ConnectorTenantId.of(tenantId),
        ConnectorName.of(name),
        ConnectorType.of("mock-erp"),
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(CREDENTIAL_REFERENCE),
        SyncPolicy.manual());
  }

  private static List<String> names(ListConnectorsResult result) {
    return result.connectors().stream().map(connector -> connector.name().value()).toList();
  }

  private static final class InMemoryConnectorRepository implements ConnectorRepository {

    private final Map<ConnectorId, Connector> connectors = new LinkedHashMap<>();

    @Override
    public Connector save(Connector connector) {
      connectors.put(connector.id(), connector);
      return connector;
    }

    @Override
    public Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId) {
      return Optional.ofNullable(connectors.get(connectorId))
          .filter(connector -> connector.tenantId().equals(tenantId));
    }

    @Override
    public List<Connector> findAllByTenantId(ConnectorTenantId tenantId) {
      return connectors.values().stream()
          .filter(connector -> connector.tenantId().equals(tenantId))
          .toList();
    }

    @Override
    public boolean existsByTenantIdAndName(
        ConnectorTenantId tenantId, ConnectorName connectorName) {
      return connectors.values().stream()
          .anyMatch(
              connector ->
                  connector.tenantId().equals(tenantId) && connector.name().equals(connectorName));
    }

    Connector saved(ConnectorId connectorId) {
      return connectors.get(connectorId);
    }

    int size() {
      return connectors.size();
    }
  }
}
