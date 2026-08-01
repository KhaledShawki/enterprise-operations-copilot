package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ActivateConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.CreateConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.GetConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ListConnectorsService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.SuspendConnectorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConnectorManagementConfiguration {

  @Bean
  CreateConnectorUseCase createConnectorUseCase(ConnectorRepository connectorRepository) {
    return new CreateConnectorService(connectorRepository);
  }

  @Bean
  GetConnectorUseCase getConnectorUseCase(ConnectorRepository connectorRepository) {
    return new GetConnectorService(connectorRepository);
  }

  @Bean
  ListConnectorsUseCase listConnectorsUseCase(ConnectorRepository connectorRepository) {
    return new ListConnectorsService(connectorRepository);
  }

  @Bean
  ActivateConnectorUseCase activateConnectorUseCase(ConnectorRepository connectorRepository) {
    return new ActivateConnectorService(connectorRepository);
  }

  @Bean
  SuspendConnectorUseCase suspendConnectorUseCase(ConnectorRepository connectorRepository) {
    return new SuspendConnectorService(connectorRepository);
  }
}
