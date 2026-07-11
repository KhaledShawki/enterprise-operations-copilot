package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public record CreateTenantCommand(String tenantName) {

  public CreateTenantCommand {
    if (tenantName == null) {
      throw new IllegalArgumentException("Tenant name cannot be null");
    }
  }
}
