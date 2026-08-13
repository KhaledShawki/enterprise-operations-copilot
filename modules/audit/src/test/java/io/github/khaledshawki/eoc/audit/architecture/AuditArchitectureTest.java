package io.github.khaledshawki.eoc.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class AuditArchitectureTest {
  private static final String AUDIT_PACKAGE = "io.github.khaledshawki.eoc.audit";
  private static final String APPLICATION_PACKAGE = AUDIT_PACKAGE + ".application..";
  private static final String INPUT_PORT_PACKAGE = AUDIT_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE = AUDIT_PACKAGE + ".application.port.out..";
  private static final JavaClasses AUDIT_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(AUDIT_PACKAGE);

  @Test
  void moduleDoesNotDependOnFrameworksOrOtherBoundedContexts() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "org.hibernate..",
            "org.apache.kafka..",
            "com.fasterxml.jackson..",
            "tools.jackson..",
            "io.modelcontextprotocol..",
            "io.github.khaledshawki.eoc.copilot..",
            "io.github.khaledshawki.eoc.analytics..",
            "io.github.khaledshawki.eoc.operations..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.tenantaccess..")
        .because("Audit must remain a framework-neutral independent bounded context")
        .check(AUDIT_CLASSES);
  }

  @Test
  void applicationDependsOnlyOnAuditApplicationTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(APPLICATION_PACKAGE, "java..")
        .check(AUDIT_CLASSES);
  }

  @Test
  void portsAreInterfaces() {
    classes()
        .that()
        .resideInAnyPackage(INPUT_PORT_PACKAGE, OUTPUT_PORT_PACKAGE)
        .should()
        .beInterfaces()
        .check(AUDIT_CLASSES);
  }
}
