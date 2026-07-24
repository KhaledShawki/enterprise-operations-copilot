package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.application.exception.ExternalIdentityAlreadyExistsException;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PlatformUserPersistenceAdapter implements PlatformUserRepository {

  private static final String EXTERNAL_IDENTITY_UNIQUE_CONSTRAINT =
      "uk_platform_users_external_identity";

  private final SpringDataPlatformUserRepository platformUserRepository;
  private final PlatformUserPersistenceMapper platformUserPersistenceMapper;
  private final Clock clock;

  PlatformUserPersistenceAdapter(
      SpringDataPlatformUserRepository platformUserRepository,
      PlatformUserPersistenceMapper platformUserPersistenceMapper,
      Clock clock) {
    this.platformUserRepository =
        Objects.requireNonNull(platformUserRepository, "Platform user repository cannot be null");
    this.platformUserPersistenceMapper =
        Objects.requireNonNull(
            platformUserPersistenceMapper, "Platform user persistence mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  @Transactional
  public PlatformUser save(PlatformUser user) {
    Objects.requireNonNull(user, "Platform user cannot be null");

    Instant now = clock.instant();

    PlatformUserJpaEntity entity =
        platformUserRepository
            .findById(user.id().value())
            .map(existing -> platformUserPersistenceMapper.updateEntity(user, existing, now))
            .orElseGet(() -> platformUserPersistenceMapper.toEntity(user, now));

    try {
      PlatformUserJpaEntity savedUser = platformUserRepository.saveAndFlush(entity);
      return platformUserPersistenceMapper.toDomain(savedUser);
    } catch (DataIntegrityViolationException exception) {
      if (PersistenceConstraintViolationDetector.hasConstraintName(
          exception, EXTERNAL_IDENTITY_UNIQUE_CONSTRAINT)) {
        throw new ExternalIdentityAlreadyExistsException(user.externalIdentity(), exception);
      }

      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PlatformUser> findById(PlatformUserId userId) {
    Objects.requireNonNull(userId, "Platform user id cannot be null");

    return platformUserRepository
        .findById(userId.value())
        .map(platformUserPersistenceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity) {
    Objects.requireNonNull(externalIdentity, "External identity cannot be null");

    return platformUserRepository
        .findByIssuerAndSubject(externalIdentity.issuer(), externalIdentity.subject())
        .map(platformUserPersistenceMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByExternalIdentity(ExternalIdentity externalIdentity) {
    Objects.requireNonNull(externalIdentity, "External identity cannot be null");

    return platformUserRepository.existsByIssuerAndSubject(
        externalIdentity.issuer(), externalIdentity.subject());
  }
}
