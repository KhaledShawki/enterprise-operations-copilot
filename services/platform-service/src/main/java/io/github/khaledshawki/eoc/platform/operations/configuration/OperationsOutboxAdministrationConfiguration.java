package io.github.khaledshawki.eoc.platform.operations.configuration;

import io.github.khaledshawki.eoc.operations.application.port.in.InspectOperationsOutboxUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxInspectionRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import io.github.khaledshawki.eoc.operations.application.service.InspectOperationsOutboxService;
import io.github.khaledshawki.eoc.operations.application.service.RecoverOperationsOutboxEventService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OperationsOutboxAdministrationConfiguration {

  @Bean
  InspectOperationsOutboxUseCase inspectOperationsOutboxUseCase(
      OperationsOutboxInspectionRepository repository) {
    return new InspectOperationsOutboxService(repository);
  }

  @Bean
  RecoverOperationsOutboxEventUseCase recoverOperationsOutboxEventUseCase(
      OperationsOutboxRecoveryRepository repository, Clock clock) {
    return new RecoverOperationsOutboxEventService(repository, UUID::randomUUID, clock);
  }
}
