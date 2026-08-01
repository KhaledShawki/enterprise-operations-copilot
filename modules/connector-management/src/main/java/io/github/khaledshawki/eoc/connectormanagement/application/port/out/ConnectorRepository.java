package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import java.util.Optional;

public interface ConnectorRepository {

  Connector save(Connector connector);

  Optional<Connector> findById(ConnectorTenantId tenantId, ConnectorId connectorId);

  boolean existsByTenantIdAndName(ConnectorTenantId tenantId, ConnectorName connectorName);
}
