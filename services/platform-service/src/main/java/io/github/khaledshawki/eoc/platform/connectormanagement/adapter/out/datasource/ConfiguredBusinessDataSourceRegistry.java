package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.datasource;

import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSourceRegistry;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
final class ConfiguredBusinessDataSourceRegistry implements BusinessDataSourceRegistry {

  private final Map<ConnectorType, BusinessDataSource> dataSources;

  ConfiguredBusinessDataSourceRegistry(List<BusinessDataSource> configuredDataSources) {
    Objects.requireNonNull(
        configuredDataSources, "Configured business data sources cannot be null");
    Map<ConnectorType, BusinessDataSource> indexedDataSources = new LinkedHashMap<>();
    for (BusinessDataSource dataSource : configuredDataSources) {
      Objects.requireNonNull(dataSource, "Configured business data source cannot be null");
      BusinessDataSource duplicate =
          indexedDataSources.putIfAbsent(dataSource.supportedConnectorType(), dataSource);
      if (duplicate != null) {
        throw new IllegalStateException(
            "Multiple business data sources support connector type "
                + dataSource.supportedConnectorType().value());
      }
    }
    dataSources = Map.copyOf(indexedDataSources);
  }

  @Override
  public Optional<BusinessDataSource> findByConnectorType(ConnectorType connectorType) {
    Objects.requireNonNull(connectorType, "Connector type cannot be null");
    return Optional.ofNullable(dataSources.get(connectorType));
  }
}
