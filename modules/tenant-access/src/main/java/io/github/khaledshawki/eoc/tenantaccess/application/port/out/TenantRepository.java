package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.Tenant;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantId;
import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantName;
import java.util.Optional;

public interface TenantRepository {

  Tenant save(Tenant tenant);

  Optional<Tenant> findById(TenantId tenantId);

  boolean existsByName(TenantName tenantName);
}
