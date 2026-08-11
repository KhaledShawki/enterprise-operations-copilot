package io.github.khaledshawki.eoc.analytics.application.port.out;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.BusinessPartnerProjection;
import java.util.Optional;
import java.util.UUID;

public interface BusinessPartnerProjectionRepository {

  Optional<BusinessPartnerProjection> findById(AnalyticsTenantId tenantId, UUID businessPartnerId);

  /**
   * Conditionally persists the projection. Version zero means the row must not exist; otherwise the
   * currently stored aggregate version must equal {@code expectedCurrentVersion}.
   */
  boolean saveIfCurrentVersion(BusinessPartnerProjection projection, long expectedCurrentVersion);
}
