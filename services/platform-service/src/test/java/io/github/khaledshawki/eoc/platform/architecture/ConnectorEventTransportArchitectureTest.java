package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ConsumeConnectorIntegrationEventUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.InspectConnectorDeadLettersUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorDeadLetterReplayBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestConnectorDeadLetterReplayUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReader;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayPublisher;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorDeadLetterReplayRepository;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventInbox;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import org.junit.jupiter.api.Test;

class ConnectorEventTransportArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String CONNECTOR_MESSAGING_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter..messaging..";
  private static final String CONNECTOR_KAFKA_INBOUND_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.in.messaging.kafka..";
  private static final String CONNECTOR_KAFKA_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter..messaging.kafka..";
  private static final String CONNECTOR_PERSISTENCE_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.out.persistence..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void connectorEventPublishersLiveOnlyInMessagingAdapters() {
    classes()
        .that()
        .implement(ConnectorIntegrationEventPublisher.class)
        .should()
        .resideInAPackage(CONNECTOR_MESSAGING_PACKAGE)
        .because("event publication is a transport adapter responsibility")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorInboxImplementationRemainsAPersistenceAdapter() {
    classes()
        .that()
        .implement(ConnectorIntegrationEventInbox.class)
        .should()
        .resideInAPackage(CONNECTOR_PERSISTENCE_PACKAGE)
        .because("the durable idempotent inbox belongs to persistence")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void messagingAdaptersDoNotDependOnConcreteConnectorPersistence() {
    noClasses()
        .that()
        .resideInAPackage(CONNECTOR_MESSAGING_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CONNECTOR_PERSISTENCE_PACKAGE)
        .because("transport adapters must collaborate with the inbox through connector-owned ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void inboundKafkaAdapterEntersThroughTheApplicationInputPort() {
    classes()
        .that()
        .haveSimpleName("KafkaConnectorIntegrationEventConsumer")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(ConsumeConnectorIntegrationEventUseCase.class)
        .andShould()
        .resideInAPackage(CONNECTOR_KAFKA_INBOUND_PACKAGE)
        .because("an inbound transport must not bypass the connector application boundary")
        .check(PLATFORM_CLASSES);

    noClasses()
        .that()
        .resideInAPackage(CONNECTOR_KAFKA_INBOUND_PACKAGE)
        .should()
        .dependOnClassesThat()
        .areAssignableTo(ConnectorIntegrationEventInbox.class)
        .because("the Kafka listener must not invoke an application output port directly")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void deadLetterRecoveryKeepsWebAndSchedulingAdaptersOnApplicationInputPorts() {
    classes()
        .that()
        .haveSimpleName("ConnectorDeadLetterRecoveryController")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(InspectConnectorDeadLettersUseCase.class)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(RequestConnectorDeadLetterReplayUseCase.class)
        .because("dead-letter administration must enter through application input ports")
        .check(PLATFORM_CLASSES);

    classes()
        .that()
        .haveSimpleName("ConnectorDeadLetterReplayScheduledRelay")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(PublishConnectorDeadLetterReplayBatchUseCase.class)
        .because("the scheduled replay relay is an inbound adapter")
        .check(PLATFORM_CLASSES);

    noClasses()
        .that()
        .haveSimpleName("ConnectorDeadLetterRecoveryController")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(ConnectorDeadLetterReader.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(ConnectorDeadLetterReplayRepository.class)
        .because("web adapters must not bypass the recovery application boundary")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void deadLetterKafkaAndPersistenceDetailsRemainOutputAdapters() {
    classes()
        .that()
        .implement(ConnectorDeadLetterReader.class)
        .or()
        .implement(ConnectorDeadLetterReplayPublisher.class)
        .should()
        .resideInAPackage(CONNECTOR_MESSAGING_PACKAGE)
        .because("Kafka DLT access is a replaceable messaging adapter detail")
        .check(PLATFORM_CLASSES);

    classes()
        .that()
        .implement(ConnectorDeadLetterReplayRepository.class)
        .should()
        .resideInAPackage(CONNECTOR_PERSISTENCE_PACKAGE)
        .because("durable replay audit and fencing belong to persistence")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void kafkaTechnologyDoesNotLeakOutsideTheKafkaAdapter() {
    noClasses()
        .that()
        .resideOutsideOfPackage(CONNECTOR_KAFKA_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..")
        .because("Kafka must remain a replaceable transport-adapter detail")
        .check(PLATFORM_CLASSES);
  }
}
