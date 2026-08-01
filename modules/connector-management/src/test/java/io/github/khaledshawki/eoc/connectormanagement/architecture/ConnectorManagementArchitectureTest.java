package io.github.khaledshawki.eoc.connectormanagement.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ConnectorManagementArchitectureTest {

  private static final String CONNECTOR_PACKAGE = "io.github.khaledshawki.eoc.connectormanagement";
  private static final String DOMAIN_PACKAGE = CONNECTOR_PACKAGE + ".domain..";
  private static final String APPLICATION_PACKAGE = CONNECTOR_PACKAGE + ".application..";
  private static final String OUTPUT_PORT_PACKAGE = CONNECTOR_PACKAGE + ".application.port.out..";

  private static final JavaClasses CONNECTOR_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(CONNECTOR_PACKAGE);

  @Test
  void domainDependsOnlyOnDomainTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(DOMAIN_PACKAGE, "java..")
        .because("the connector domain must remain independent of application and infrastructure")
        .check(CONNECTOR_CLASSES);
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
            "org.hibernate..")
        .because("connector-management must remain framework independent")
        .check(CONNECTOR_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnAnotherBoundedContext() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.github.khaledshawki.eoc.tenantaccess..")
        .because("bounded contexts must communicate through explicit contracts")
        .check(CONNECTOR_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnApplicationDomainAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE, DOMAIN_PACKAGE, "java..")
        .because("connector application contracts must remain infrastructure independent")
        .check(CONNECTOR_CLASSES);
  }

  @Test
  void outputPortsAreInterfaces() {
    classes()
        .that()
        .resideInAPackage(OUTPUT_PORT_PACKAGE)
        .should()
        .beInterfaces()
        .because("outbound infrastructure must implement application-owned contracts")
        .check(CONNECTOR_CLASSES);
  }
}
