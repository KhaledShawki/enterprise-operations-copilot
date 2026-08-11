package io.github.khaledshawki.eoc.analytics.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AnalyticsArchitectureTest {

  private static final String ANALYTICS_PACKAGE = "io.github.khaledshawki.eoc.analytics";
  private static final String DOMAIN_PACKAGE = ANALYTICS_PACKAGE + ".domain..";
  private static final String APPLICATION_PACKAGE = ANALYTICS_PACKAGE + ".application..";
  private static final String INPUT_PORT_PACKAGE = ANALYTICS_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE = ANALYTICS_PACKAGE + ".application.port.out..";

  private static final JavaClasses ANALYTICS_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ANALYTICS_PACKAGE);

  @Test
  void domainDependsOnlyOnDomainTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(DOMAIN_PACKAGE, "java..")
        .because("the Analytics domain must remain independent of application and infrastructure")
        .check(ANALYTICS_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnFrameworks() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.apache.kafka..",
            "com.fasterxml.jackson..",
            "tools.jackson..",
            "io.micrometer..")
        .because("the Analytics module must remain framework and transport independent")
        .check(ANALYTICS_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnAnotherBoundedContext() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.tenantaccess..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.operations..")
        .because("Analytics must own projections instead of importing another context's model")
        .check(ANALYTICS_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnApplicationDomainAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE, DOMAIN_PACKAGE, "java..")
        .because("Analytics application contracts must remain infrastructure independent")
        .check(ANALYTICS_CLASSES);
  }

  @Test
  void outputPortsAreInterfaces() {
    classes()
        .that()
        .resideInAPackage(OUTPUT_PORT_PACKAGE)
        .should()
        .beInterfaces()
        .because("outbound infrastructure must implement Analytics-owned contracts")
        .check(ANALYTICS_CLASSES);
  }

  @Test
  void inputUseCasesAreInterfaces() {
    classes()
        .that()
        .resideInAPackage(INPUT_PORT_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("UseCase")
        .should()
        .beInterfaces()
        .because("inbound adapters must depend on Analytics-owned input ports")
        .check(ANALYTICS_CLASSES);
  }
}
