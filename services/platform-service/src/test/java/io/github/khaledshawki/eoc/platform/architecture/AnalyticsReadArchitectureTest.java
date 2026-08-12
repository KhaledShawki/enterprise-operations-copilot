package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.analytics.application.port.out.ReceivableSummaryReadPort;
import org.junit.jupiter.api.Test;

class AnalyticsReadArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String ANALYTICS_PACKAGE = "io.github.khaledshawki.eoc.analytics";
  private static final String ANALYTICS_INPUT_PORT_PACKAGE =
      ANALYTICS_PACKAGE + ".application.port.in..";
  private static final String ANALYTICS_OUTPUT_PORT_PACKAGE =
      ANALYTICS_PACKAGE + ".application.port.out..";
  private static final String ANALYTICS_APPLICATION_SERVICE_PACKAGE =
      ANALYTICS_PACKAGE + ".application.service..";
  private static final String ANALYTICS_WEB_PACKAGE =
      PLATFORM_PACKAGE + ".analytics.adapter.in.web..";
  private static final String ANALYTICS_PERSISTENCE_PACKAGE =
      PLATFORM_PACKAGE + ".analytics.adapter.out.persistence..";
  private static final String ANALYTICS_CONFIGURATION_PACKAGE =
      PLATFORM_PACKAGE + ".analytics.configuration..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void analyticsWebControllersUseAnalyticsInputPorts() {
    classes()
        .that()
        .resideInAPackage(ANALYTICS_WEB_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ANALYTICS_INPUT_PORT_PACKAGE)
        .because("Analytics web controllers must invoke Analytics input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void analyticsWebAdaptersDoNotBypassInputPorts() {
    noClasses()
        .that()
        .resideInAPackage(ANALYTICS_WEB_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            ANALYTICS_APPLICATION_SERVICE_PACKAGE,
            ANALYTICS_OUTPUT_PORT_PACKAGE,
            ANALYTICS_PERSISTENCE_PACKAGE,
            ANALYTICS_CONFIGURATION_PACKAGE,
            "io.github.khaledshawki.eoc.operations..")
        .because("Analytics web adapters must remain independent of services and infrastructure")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void receivableReadPersistenceImplementsAnAnalyticsOutputPort() {
    classes()
        .that()
        .resideInAPackage(ANALYTICS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("ReceivableReadPersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(ANALYTICS_OUTPUT_PORT_PACKAGE))
        .check(PLATFORM_CLASSES);
  }

  @Test
  void receivableReadPersistenceImplementsTheSummaryReadPort() {
    classes()
        .that()
        .resideInAPackage(ANALYTICS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("ReceivableReadPersistenceAdapter")
        .should()
        .implement(ReceivableSummaryReadPort.class)
        .check(PLATFORM_CLASSES);
  }

  @Test
  void analyticsReadPersistenceDoesNotDependOnOperationsImplementationTypes() {
    noClasses()
        .that()
        .resideInAPackage(ANALYTICS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("ReceivableReadPersistenceAdapter")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.github.khaledshawki.eoc.operations..")
        .because("Analytics reads must remain independent of the Operations implementation module")
        .check(PLATFORM_CLASSES);
  }
}
