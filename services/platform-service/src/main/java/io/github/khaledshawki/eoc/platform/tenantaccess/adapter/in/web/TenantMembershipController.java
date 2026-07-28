package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  public TenantMembershipController(
      AssignTenantMembershipUseCase assignTenantMembershipUseCase,
      GetTenantMembershipUseCase getTenantMembershipUseCase) {
    this.assignTenantMembershipUseCase =
        Objects.requireNonNull(
            assignTenantMembershipUseCase, "Assign tenant membership use case cannot be null");

    this.getTenantMembershipUseCase =
        Objects.requireNonNull(
            getTenantMembershipUseCase, "Get tenant membership use case cannot be null");
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
}
