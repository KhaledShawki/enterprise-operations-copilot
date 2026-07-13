package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

  boolean existsByTenantKey(String tenantKey);
}
