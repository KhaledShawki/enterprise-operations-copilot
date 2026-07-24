package io.github.khaledshawki.eoc.tenantaccess.application.port.in;

public interface ProvisionPlatformUserUseCase {

  ProvisionPlatformUserResult provision(ProvisionPlatformUserCommand command);
}
