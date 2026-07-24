package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.ExternalIdentity;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUser;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import java.util.Optional;

public interface PlatformUserRepository {

  PlatformUser save(PlatformUser user);

  Optional<PlatformUser> findById(PlatformUserId userId);

  Optional<PlatformUser> findByExternalIdentity(ExternalIdentity externalIdentity);

  boolean existsByExternalIdentity(ExternalIdentity externalIdentity);
}
