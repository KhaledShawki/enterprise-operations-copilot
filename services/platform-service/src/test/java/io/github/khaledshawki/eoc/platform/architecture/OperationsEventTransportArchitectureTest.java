package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.operations.application.port.in.PublishOperationsOutboxBatchUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsIntegrationEventPublisher;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRepository;
import org.junit.jupiter.api.Test;

class OperationsEventTransportArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String OPERATIONS_KAFKA_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.out.messaging.kafka..";
  private static final String OPERATIONS_SCHEDULING_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.in.scheduling..";
  private static final String OPERATIONS_PERSISTENCE_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.out.persistence..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void operationsKafkaPublisherImplementsTheOperationsOwnedOutputPort() {
    classes()
        .that()
        .implement(OperationsIntegrationEventPublisher.class)
        .should()
        .resideInAPackage(OPERATIONS_KAFKA_PACKAGE)
        .because("Kafka publication is an Operations output-adapter responsibility")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void scheduledRelayEntersThroughTheOperationsApplicationInputPort() {
    classes()
        .that()
        .haveSimpleName("OperationsOutboxScheduledRelay")
        .should()
        .resideInAPackage(OPERATIONS_SCHEDULING_PACKAGE)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(PublishOperationsOutboxBatchUseCase.class)
        .because("a scheduled relay is an inbound adapter")
        .check(PLATFORM_CLASSES);

    noClasses()
        .that()
        .resideInAPackage(OPERATIONS_SCHEDULING_PACKAGE)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(OperationsIntegrationEventPublisher.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(OperationsOutboxRepository.class)
        .because("the scheduled adapter must not bypass the Operations application boundary")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void operationsKafkaAdapterDoesNotDependOnConcretePersistence() {
    noClasses()
        .that()
        .resideInAPackage(OPERATIONS_KAFKA_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .because("transport and persistence adapters collaborate only through Operations ports")
        .check(PLATFORM_CLASSES);
  }
}
