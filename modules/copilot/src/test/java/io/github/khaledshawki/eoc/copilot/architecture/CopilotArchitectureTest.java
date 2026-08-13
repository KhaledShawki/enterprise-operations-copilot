package io.github.khaledshawki.eoc.copilot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CopilotArchitectureTest {
  private static final String COPILOT_PACKAGE = "io.github.khaledshawki.eoc.copilot";
  private static final String APPLICATION_PACKAGE = COPILOT_PACKAGE + ".application..";
  private static final String INPUT_PORT_PACKAGE = COPILOT_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE = COPILOT_PACKAGE + ".application.port.out..";

  private static final JavaClasses COPILOT_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(COPILOT_PACKAGE);

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
            "io.micrometer..",
            "io.modelcontextprotocol..")
        .because("the Copilot module must remain framework and transport independent")
        .check(COPILOT_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnAnotherBoundedContext() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.tenantaccess..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.operations..",
            "io.github.khaledshawki.eoc.analytics..")
        .because("Copilot tools must cross bounded contexts only through platform bridges")
        .check(COPILOT_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnApplicationTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE, "java..")
        .because("Copilot application contracts must remain infrastructure independent")
        .check(COPILOT_CLASSES);
  }

  @Test
  void outputPortsAreInterfaces() {
    classes()
        .that()
        .resideInAPackage(OUTPUT_PORT_PACKAGE)
        .should()
        .beInterfaces()
        .because("platform bridges must implement Copilot-owned output contracts")
        .check(COPILOT_CLASSES);
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
        .because("future orchestration adapters must depend on Copilot-owned input ports")
        .check(COPILOT_CLASSES);
  }
}
