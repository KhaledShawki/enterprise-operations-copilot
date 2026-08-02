package io.github.khaledshawki.eoc.connectormanagement.application.port.out;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import java.util.Optional;

/** Resolves a source adapter without exposing framework discovery to the application core. */
public interface BusinessDataSourceRegistry {

  Optional<BusinessDataSource> findByConnectorType(ConnectorType connectorType);
}
