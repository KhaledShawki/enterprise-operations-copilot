package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ActivateTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ReplaceTenantMembershipRolesUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.SuspendTenantMembershipUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(
    path = "/api/v1/tenants/{tenantId}/memberships",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class TenantMembershipController {

  private final AssignTenantMembershipUseCase assignTenantMembershipUseCase;

  private final GetTenantMembershipUseCase getTenantMembershipUseCase;

  private final SuspendTenantMembershipUseCase suspendTenantMembershipUseCase;

  private final ActivateTenantMembershipUseCase activateTenantMembershipUseCase;

  private final ReplaceTenantMembershipRolesUseCase replaceTenantMembershipRolesUseCase;

  public TenantMembershipController(
      AssignTenantMembershipUseCase assignTenantMembershipUseCase,
      GetTenantMembershipUseCase getTenantMembershipUseCase,
      SuspendTenantMembershipUseCase suspendTenantMembershipUseCase,
      ActivateTenantMembershipUseCase activateTenantMembershipUseCase,
      ReplaceTenantMembershipRolesUseCase replaceTenantMembershipRolesUseCase) {
    this.assignTenantMembershipUseCase =
        Objects.requireNonNull(
            assignTenantMembershipUseCase, "Assign tenant membership use case cannot be null");

    this.getTenantMembershipUseCase =
        Objects.requireNonNull(
            getTenantMembershipUseCase, "Get tenant membership use case cannot be null");

    this.suspendTenantMembershipUseCase =
        Objects.requireNonNull(
            suspendTenantMembershipUseCase, "Suspend tenant membership use case cannot be null");

    this.activateTenantMembershipUseCase =
        Objects.requireNonNull(
            activateTenantMembershipUseCase, "Activate tenant membership use case cannot be null");

    this.replaceTenantMembershipRolesUseCase =
        Objects.requireNonNull(
            replaceTenantMembershipRolesUseCase,
            "Replace tenant membership roles use case cannot be null");
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TenantMembershipResponse> assignTenantMembership(
      @PathVariable UUID tenantId, @Valid @RequestBody AssignTenantMembershipRequest request) {

    AssignTenantMembershipCommand command =
        new AssignTenantMembershipCommand(tenantId, request.platformUserId());

    AssignTenantMembershipResult result = assignTenantMembershipUseCase.assign(command);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{membershipId}")
            .buildAndExpand(result.membershipId().value())
            .toUri();

    return ResponseEntity.created(location).body(TenantMembershipResponse.from(result));
  }

  @GetMapping("/{membershipId}")
  public TenantMembershipResponse getTenantMembership(
      @PathVariable UUID tenantId, @PathVariable UUID membershipId) {

    GetTenantMembershipQuery query = new GetTenantMembershipQuery(tenantId, membershipId);

    GetTenantMembershipResult result = getTenantMembershipUseCase.get(query);

    return TenantMembershipResponse.from(result);
  }

  @PostMapping("/{membershipId}/suspension")
  public TenantMembershipResponse suspendTenantMembership(
      @PathVariable UUID tenantId, @PathVariable UUID membershipId) {

    SuspendTenantMembershipCommand command =
        new SuspendTenantMembershipCommand(tenantId, membershipId);

    SuspendTenantMembershipResult result = suspendTenantMembershipUseCase.suspend(command);

    return TenantMembershipResponse.from(result);
  }

  @PostMapping("/{membershipId}/activation")
  public TenantMembershipResponse activateTenantMembership(
      @PathVariable UUID tenantId, @PathVariable UUID membershipId) {

    ActivateTenantMembershipCommand command =
        new ActivateTenantMembershipCommand(tenantId, membershipId);

    ActivateTenantMembershipResult result = activateTenantMembershipUseCase.activate(command);

    return TenantMembershipResponse.from(result);
  }

  @PutMapping(path = "/{membershipId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
  public TenantMembershipResponse replaceTenantMembershipRoles(
      @PathVariable UUID tenantId,
      @PathVariable UUID membershipId,
      @Valid @RequestBody ReplaceTenantMembershipRolesRequest request) {

    ReplaceTenantMembershipRolesCommand command =
        new ReplaceTenantMembershipRolesCommand(tenantId, membershipId, request.roles());

    ReplaceTenantMembershipRolesResult result =
        replaceTenantMembershipRolesUseCase.replaceRoles(command);

    return TenantMembershipResponse.from(result);
  }
}
