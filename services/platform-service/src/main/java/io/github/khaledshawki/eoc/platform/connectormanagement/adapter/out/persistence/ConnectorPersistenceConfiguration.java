package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConnectorPersistenceConfiguration {

  @Bean
  ConnectorPersistenceMapper connectorPersistenceMapper() {
    return new ConnectorPersistenceMapper();
  }

  @Bean
  ImportRunPersistenceMapper importRunPersistenceMapper() {
    return new ImportRunPersistenceMapper();
  }
}
