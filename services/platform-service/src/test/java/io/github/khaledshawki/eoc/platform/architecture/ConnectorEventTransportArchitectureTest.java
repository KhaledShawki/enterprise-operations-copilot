package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventInbox;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorIntegrationEventPublisher;
import org.junit.jupiter.api.Test;

class ConnectorEventTransportArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String CONNECTOR_MESSAGING_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.out.messaging..";
  private static final String CONNECTOR_KAFKA_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.out.messaging.kafka..";
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
  void kafkaTechnologyDoesNotLeakOutsideTheKafkaAdapter() {
    noClasses()
        .that()
        .resideOutsideOfPackage(CONNECTOR_KAFKA_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.apache.kafka..", "org.springframework.kafka..")
        .because("Kafka must remain an outbound infrastructure detail")
        .check(PLATFORM_CLASSES);
  }
}
