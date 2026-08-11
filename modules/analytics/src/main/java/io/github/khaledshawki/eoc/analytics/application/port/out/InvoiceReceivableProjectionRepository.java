package io.github.khaledshawki.eoc.analytics.application.port.out;

import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceReceivableProjectionRepository {

  Optional<InvoiceReceivableProjection> findById(AnalyticsTenantId tenantId, UUID invoiceId);

  /**
   * Conditionally persists the projection. Version zero means the row must not exist; otherwise the
   * currently stored aggregate version must equal {@code expectedCurrentVersion}.
   */
  boolean saveIfCurrentVersion(InvoiceReceivableProjection projection, long expectedCurrentVersion);
}
