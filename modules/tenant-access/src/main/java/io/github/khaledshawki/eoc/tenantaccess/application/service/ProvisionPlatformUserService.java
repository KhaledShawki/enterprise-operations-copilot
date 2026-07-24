package io.github.khaledshawki.eoc.tenantaccess.application.service;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.ExternalIdentityAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserCommand;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserResult;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import java.util.Objects;

public final class ProvisionPlatformUserService implements ProvisionPlatformUserUseCase {

  private final PlatformUserRepository platformUserRepository;

  public ProvisionPlatformUserService(PlatformUserRepository platformUserRepository) {
    this.platformUserRepository =
        Objects.requireNonNull(platformUserRepository, "Platform user repository cannot be null");
  }

  @Override
  public ProvisionPlatformUserResult provision(ProvisionPlatformUserCommand command) {
    Objects.requireNonNull(command, "Command cannot be null");

    ExternalIdentity externalIdentity = ExternalIdentity.of(command.issuer(), command.subject());

    return platformUserRepository
        .findByExternalIdentity(externalIdentity)
        .map(user -> toResult(user, false))
        .orElseGet(() -> createOrResolveConcurrentUser(externalIdentity));
  }

  private ProvisionPlatformUserResult createOrResolveConcurrentUser(
      ExternalIdentity externalIdentity) {
    try {
      PlatformUser createdUser = platformUserRepository.save(PlatformUser.create(externalIdentity));

      return toResult(createdUser, true);
    } catch (ExternalIdentityAlreadyExistsException exception) {
      PlatformUser concurrentWinner =
          platformUserRepository
              .findByExternalIdentity(externalIdentity)
              .orElseThrow(() -> exception);

      return toResult(concurrentWinner, false);
    }
  }

  private static ProvisionPlatformUserResult toResult(PlatformUser user, boolean created) {
    return new ProvisionPlatformUserResult(
        user.id(), user.externalIdentity(), user.status(), created);
  }
}
