package io.github.khaledshawki.eoc.operations.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class OperationsArchitectureTest {

  private static final String OPERATIONS_PACKAGE = "io.github.khaledshawki.eoc.operations";
  private static final String DOMAIN_PACKAGE = OPERATIONS_PACKAGE + ".domain..";
  private static final String APPLICATION_PACKAGE = OPERATIONS_PACKAGE + ".application..";
  private static final String INPUT_PORT_PACKAGE = OPERATIONS_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE = OPERATIONS_PACKAGE + ".application.port.out..";

  private static final JavaClasses OPERATIONS_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(OPERATIONS_PACKAGE);

  @Test
  void domainDependsOnlyOnDomainTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(DOMAIN_PACKAGE, "java..")
        .because("the Operations domain must remain independent of application and infrastructure")
        .check(OPERATIONS_CLASSES);
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
            "io.micrometer..")
        .because("the Operations module must remain framework and transport independent")
        .check(OPERATIONS_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnAnotherBoundedContext() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.tenantaccess..",
            "io.github.khaledshawki.eoc.connectormanagement..")
        .because("Operations must own its contracts instead of sharing another context's model")
        .check(OPERATIONS_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnApplicationDomainAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE, DOMAIN_PACKAGE, "java..")
        .because("Operations application contracts must remain infrastructure independent")
        .check(OPERATIONS_CLASSES);
  }

  @Test
  void outputPortsAreInterfaces() {
    classes()
        .that()
        .resideInAPackage(OUTPUT_PORT_PACKAGE)
        .should()
        .beInterfaces()
        .because("outbound infrastructure must implement Operations-owned contracts")
        .check(OPERATIONS_CLASSES);
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
        .because("inbound adapters must depend on Operations-owned input ports")
        .check(OPERATIONS_CLASSES);
  }
}
