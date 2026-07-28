package io.github.khaledshawki.eoc.tenantaccess.application.port.out;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.PlatformUserId;
import java.util.List;

public interface AccessibleTenantQueryRepository {

  List<AccessibleTenantProjection> findAllByPlatformUserId(PlatformUserId platformUserId);
}
