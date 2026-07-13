package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

import java.util.Objects;

public record CreateTenantCommand(String tenantKey, String tenantName) {

  public CreateTenantCommand {
    Objects.requireNonNull(tenantKey, "Tenant key cannot be null");
    Objects.requireNonNull(tenantName, "Tenant name cannot be null");
  }
}
