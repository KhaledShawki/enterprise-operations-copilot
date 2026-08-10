package io.github.khaledshawki.eoc.platform.connectormanagement.configuration;

import io.github.khaledshawki.eoc.connectormanagement.application.model.importing.ImportRetryPolicy;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ActivateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.CreateConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ExecuteImportRunUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.GetConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ListConnectorsUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.SuspendConnectorUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSourceRegistry;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessPartnerImportPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorAuthorizationPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorEventIdGenerator;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventInbox;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ImportRunRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.InvoiceImportPort;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.PaymentImportPort;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ActivateConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ConsumeConnectorIntegrationEventService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.CreateConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ExecuteImportRunService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.GetConnectorService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ImportRunLifecycleService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.ListConnectorsService;
import io.github.khaledshawki.eoc.connectormanagement.application.service.SuspendConnectorService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConnectorManagementConfiguration {

  @Bean
  ConsumeConnectorIntegrationEventUseCase consumeConnectorIntegrationEventUseCase(
      ConnectorIntegrationEventInbox inbox) {
    return new ConsumeConnectorIntegrationEventService(inbox);
  }

  @Bean
  CreateConnectorUseCase createConnectorUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new CreateConnectorService(connectorRepository, connectorAuthorizationPort);
  }

  @Bean
  GetConnectorUseCase getConnectorUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new GetConnectorService(connectorRepository, connectorAuthorizationPort);
  }

  @Bean
  ListConnectorsUseCase listConnectorsUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new ListConnectorsService(connectorRepository, connectorAuthorizationPort);
  }

  @Bean
  ActivateConnectorUseCase activateConnectorUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new ActivateConnectorService(connectorRepository, connectorAuthorizationPort);
  }

  @Bean
  SuspendConnectorUseCase suspendConnectorUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort) {
    return new SuspendConnectorService(connectorRepository, connectorAuthorizationPort);
  }

  @Bean
  ImportRunLifecycleUseCase importRunLifecycleUseCase(
      ConnectorRepository connectorRepository,
      ImportRunRepository importRunRepository,
      ConnectorEventIdGenerator connectorEventIdGenerator,
      Clock clock) {
    return new ImportRunLifecycleService(
        connectorRepository, importRunRepository, connectorEventIdGenerator, clock);
  }

  @Bean
  ConnectorEventIdGenerator connectorEventIdGenerator() {
    return UUID::randomUUID;
  }

  @Bean
  ExecuteImportRunUseCase executeImportRunUseCase(
      ConnectorRepository connectorRepository,
      ConnectorAuthorizationPort connectorAuthorizationPort,
      ImportRunLifecycleUseCase importRunLifecycleUseCase,
      BusinessDataSourceRegistry businessDataSourceRegistry,
      BusinessPartnerImportPort businessPartnerImportPort,
      InvoiceImportPort invoiceImportPort,
      PaymentImportPort paymentImportPort,
      Clock clock) {
    return new ExecuteImportRunService(
        connectorRepository,
        connectorAuthorizationPort,
        importRunLifecycleUseCase,
        businessDataSourceRegistry,
        businessPartnerImportPort,
        invoiceImportPort,
        paymentImportPort,
        new ImportRetryPolicy(3, Duration.ofMinutes(1)),
        clock);
  }
}
