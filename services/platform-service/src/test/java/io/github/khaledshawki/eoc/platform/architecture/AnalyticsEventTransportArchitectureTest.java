package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AnalyticsEventTransportArchitectureTest {

  private static final String PLATFORM_ANALYTICS_PACKAGE =
      "io.github.khaledshawki.eoc.platform.analytics";
  private static final String ANALYTICS_INPUT_PORT_PACKAGE =
      "io.github.khaledshawki.eoc.analytics.application.port.in..";
  private static final String ANALYTICS_OUTPUT_PORT_PACKAGE =
      "io.github.khaledshawki.eoc.analytics.application.port.out..";
  private static final String ANALYTICS_APPLICATION_SERVICE_PACKAGE =
      "io.github.khaledshawki.eoc.analytics.application.service..";
  private static final String KAFKA_INPUT_PACKAGE =
      PLATFORM_ANALYTICS_PACKAGE + ".adapter.in.messaging.kafka..";
  private static final String PERSISTENCE_PACKAGE =
      PLATFORM_ANALYTICS_PACKAGE + ".adapter.out.persistence..";

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_ANALYTICS_PACKAGE);

  @Test
  void analyticsAdaptersDoNotImportAnotherBoundedContextsImplementation() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.operations..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.tenantaccess..")
        .because(
            "Analytics adapters must translate public wire contracts into Analytics-owned types")
        .check(CLASSES);
  }

  @Test
  void analyticsTransportDoesNotDependOnWebOrJpaEntityApis() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web..", "jakarta.persistence..", "org.hibernate..")
        .because("Analytics event transport is Kafka/JDBC infrastructure, not web or ORM state")
        .check(CLASSES);
  }

  @Test
  void analyticsPersistenceAdaptersImplementAnalyticsOutputPorts() {
    classes()
        .that()
        .resideInAPackage(PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("PersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(ANALYTICS_OUTPUT_PORT_PACKAGE))
        .because("Analytics persistence must implement Analytics-owned output ports")
        .check(CLASSES);
  }

  @Test
  void analyticsKafkaInboundDependsOnInputPortsNotServicesOrOutputPorts() {
    classes()
        .that()
        .resideInAPackage(KAFKA_INPUT_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Consumer")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ANALYTICS_INPUT_PORT_PACKAGE)
        .check(CLASSES);

    noClasses()
        .that()
        .resideInAPackage(KAFKA_INPUT_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ANALYTICS_APPLICATION_SERVICE_PACKAGE, ANALYTICS_OUTPUT_PORT_PACKAGE)
        .because("Kafka inbound adapters must enter Analytics through input ports")
        .check(CLASSES);
  }
}
