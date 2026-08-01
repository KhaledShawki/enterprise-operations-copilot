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
}
