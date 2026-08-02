package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
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
import io.github.khaledshawki.eoc.platform.security.adapter.in.web.JwtAuthenticatedUserMapper;
import io.github.khaledshawki.eoc.platform.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/connectors",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ConnectorController {

  private static final String ADMINISTER_CONNECTORS =
      "@tenantAccessPolicy.hasRole(authentication, #p0, 'tenant-admin')";
  private static final String READ_CONNECTORS =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final CreateConnectorUseCase createConnectorUseCase;
  private final ListConnectorsUseCase listConnectorsUseCase;
  private final GetConnectorUseCase getConnectorUseCase;
  private final ActivateConnectorUseCase activateConnectorUseCase;
  private final SuspendConnectorUseCase suspendConnectorUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  public ConnectorController(
      CreateConnectorUseCase createConnectorUseCase,
      ListConnectorsUseCase listConnectorsUseCase,
      GetConnectorUseCase getConnectorUseCase,
      ActivateConnectorUseCase activateConnectorUseCase,
      SuspendConnectorUseCase suspendConnectorUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper) {
    this.createConnectorUseCase =
        Objects.requireNonNull(createConnectorUseCase, "Create connector use case cannot be null");
    this.listConnectorsUseCase =
        Objects.requireNonNull(listConnectorsUseCase, "List connectors use case cannot be null");
    this.getConnectorUseCase =
        Objects.requireNonNull(getConnectorUseCase, "Get connector use case cannot be null");
    this.activateConnectorUseCase =
        Objects.requireNonNull(
            activateConnectorUseCase, "Activate connector use case cannot be null");
    this.suspendConnectorUseCase =
        Objects.requireNonNull(
            suspendConnectorUseCase, "Suspend connector use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(ADMINISTER_CONNECTORS)
  public ResponseEntity<ConnectorResponse> createConnector(
      @PathVariable UUID tenantId,
      @Valid @RequestBody CreateConnectorRequest request,
      JwtAuthenticationToken authentication) {
    ConnectorResult result =
        createConnectorUseCase.create(
            new CreateConnectorCommand(
                actor(authentication),
                tenantId,
                request.name(),
                request.type(),
                request.endpoint(),
                request.credentialReference(),
                request.syncPolicy().mode(),
                request.syncPolicy().interval()));

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{connectorId}")
            .buildAndExpand(result.connectorId().value())
            .toUri();

    return ResponseEntity.created(location).body(ConnectorResponse.from(result));
  }

  @GetMapping
  @PreAuthorize(READ_CONNECTORS)
  public ConnectorsResponse listConnectors(
      @PathVariable UUID tenantId, JwtAuthenticationToken authentication) {
    ListConnectorsResult result =
        listConnectorsUseCase.list(new ListConnectorsQuery(actor(authentication), tenantId));
    return ConnectorsResponse.from(result);
  }

  @GetMapping("/{connectorId}")
  @PreAuthorize(READ_CONNECTORS)
  public ConnectorResponse getConnector(
      @PathVariable UUID tenantId,
      @PathVariable UUID connectorId,
      JwtAuthenticationToken authentication) {
    ConnectorResult result =
        getConnectorUseCase.get(
            new GetConnectorQuery(actor(authentication), tenantId, connectorId));
    return ConnectorResponse.from(result);
  }

  @PostMapping("/{connectorId}/activation")
  @PreAuthorize(ADMINISTER_CONNECTORS)
  public ConnectorResponse activateConnector(
      @PathVariable UUID tenantId,
      @PathVariable UUID connectorId,
      JwtAuthenticationToken authentication) {
    ConnectorResult result =
        activateConnectorUseCase.activate(
            new ActivateConnectorCommand(actor(authentication), tenantId, connectorId));
    return ConnectorResponse.from(result);
  }

  @PostMapping("/{connectorId}/suspension")
  @PreAuthorize(ADMINISTER_CONNECTORS)
  public ConnectorResponse suspendConnector(
      @PathVariable UUID tenantId,
      @PathVariable UUID connectorId,
      JwtAuthenticationToken authentication) {
    ConnectorResult result =
        suspendConnectorUseCase.suspend(
            new SuspendConnectorCommand(actor(authentication), tenantId, connectorId));
    return ConnectorResponse.from(result);
  }

  private ConnectorActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new ConnectorActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
