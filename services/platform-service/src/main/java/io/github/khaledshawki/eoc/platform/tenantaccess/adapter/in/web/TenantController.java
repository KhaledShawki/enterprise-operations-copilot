package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.in.web;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantQuery;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantUseCase;
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
@RequestMapping(path = "/api/v1/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
public class TenantController {

  private final CreateTenantUseCase createTenantUseCase;

  private final GetTenantUseCase getTenantUseCase;

  public TenantController(
      CreateTenantUseCase createTenantUseCase, GetTenantUseCase getTenantUseCase) {
    this.createTenantUseCase =
        Objects.requireNonNull(createTenantUseCase, "Create tenant use case cannot be null");

    this.getTenantUseCase =
        Objects.requireNonNull(getTenantUseCase, "Get tenant use case cannot be null");
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CreateTenantResponse> createTenant(
      @Valid @RequestBody CreateTenantRequest request) {
    CreateTenantResult result =
        createTenantUseCase.create(
            new CreateTenantCommand(request.tenantKey(), request.displayName()));

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{tenantId}")
            .buildAndExpand(result.tenantId().value())
            .toUri();

    return ResponseEntity.created(location).body(CreateTenantResponse.from(result));
  }

  @GetMapping("/{tenantId}")
  public GetTenantResponse getTenant(@PathVariable UUID tenantId) {
    GetTenantResult result = getTenantUseCase.get(new GetTenantQuery(tenantId));

    return GetTenantResponse.from(result);
  }
}
