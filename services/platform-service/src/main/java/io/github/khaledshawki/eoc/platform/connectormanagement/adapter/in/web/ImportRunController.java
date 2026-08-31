package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.in.web;

import io.github.khaledshawki.eoc.connectormanagement.application.model.authorization.ConnectorActor;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
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
@RequestMapping(path = "/api/v1/tenants/{tenantId}", produces = MediaType.APPLICATION_JSON_VALUE)
public class ImportRunController {

  private static final String EXECUTE_IMPORTS =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', 'operations-manager')";
  private static final String READ_IMPORTS =
      "@tenantAccessPolicy.hasAnyRole(authentication, #p0, 'tenant-admin', "
          + "'operations-manager', 'auditor')";

  private final ImportRunLifecycleUseCase importRunLifecycleUseCase;
  private final ExecuteImportRunUseCase executeImportRunUseCase;
  private final JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper;

  public ImportRunController(
      ImportRunLifecycleUseCase importRunLifecycleUseCase,
      ExecuteImportRunUseCase executeImportRunUseCase,
      JwtAuthenticatedUserMapper jwtAuthenticatedUserMapper) {
    this.importRunLifecycleUseCase =
        Objects.requireNonNull(importRunLifecycleUseCase, "Import run lifecycle cannot be null");
    this.executeImportRunUseCase =
        Objects.requireNonNull(
            executeImportRunUseCase, "Execute import run use case cannot be null");
    this.jwtAuthenticatedUserMapper =
        Objects.requireNonNull(
            jwtAuthenticatedUserMapper, "JWT authenticated user mapper cannot be null");
  }

  @PostMapping(
      path = "/connectors/{connectorId}/import-runs",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(EXECUTE_IMPORTS)
  public ResponseEntity<ImportRunResponse> requestImportRun(
      @PathVariable UUID tenantId,
      @PathVariable UUID connectorId,
      @Valid @RequestBody RequestImportRunRequest request) {
    ImportRunResult result =
        importRunLifecycleUseCase.request(
            new RequestImportRunCommand(
                tenantId, connectorId, request.importType(), request.mode()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/v1/tenants/{tenantId}/import-runs/{importRunId}")
            .buildAndExpand(tenantId, result.importRunId().value())
            .toUri();
    return ResponseEntity.created(location).body(ImportRunResponse.from(result));
  }

  @GetMapping("/import-runs/{importRunId}")
  @PreAuthorize(READ_IMPORTS)
  public ImportRunResponse getImportRun(
      @PathVariable UUID tenantId, @PathVariable UUID importRunId) {
    return ImportRunResponse.from(
        importRunLifecycleUseCase.get(new ImportRunReference(tenantId, importRunId)));
  }

  @PostMapping(
      path = "/import-runs/{importRunId}/execution",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(EXECUTE_IMPORTS)
  public ImportRunResponse executeImportRun(
      @PathVariable UUID tenantId,
      @PathVariable UUID importRunId,
      @Valid @RequestBody ExecuteImportRunRequest request,
      JwtAuthenticationToken authentication) {
    return ImportRunResponse.from(
        executeImportRunUseCase.execute(
            new ExecuteImportRunCommand(
                actor(authentication), tenantId, importRunId, request.pageSize())));
  }

  private ConnectorActor actor(JwtAuthenticationToken authentication) {
    AuthenticatedUser authenticatedUser = jwtAuthenticatedUserMapper.map(authentication);
    return new ConnectorActor(authenticatedUser.issuer().toString(), authenticatedUser.subject());
  }
}
