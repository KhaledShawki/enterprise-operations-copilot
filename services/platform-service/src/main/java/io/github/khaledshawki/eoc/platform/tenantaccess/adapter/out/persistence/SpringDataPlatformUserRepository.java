package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPlatformUserRepository extends JpaRepository<PlatformUserJpaEntity, UUID> {

  Optional<PlatformUserJpaEntity> findByIssuerAndSubject(String issuer, String subject);

  boolean existsByIssuerAndSubject(String issuer, String subject);
}
