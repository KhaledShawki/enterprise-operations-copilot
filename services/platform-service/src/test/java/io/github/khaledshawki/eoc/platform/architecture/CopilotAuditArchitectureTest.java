package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CopilotAuditArchitectureTest {
  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.github.khaledshawki.eoc.platform");

  @Test
  void auditPersistenceDoesNotDependOnCopilot() {
    noClasses()
        .that()
        .resideInAPackage("..platform.audit.adapter.out.persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("io.github.khaledshawki.eoc.copilot..")
        .because("the Audit persistence adapter must persist Audit-owned contracts only")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void copilotAuditCompositionDoesNotBypassBoundedContextsOrUseProviderApis() {
    noClasses()
        .that()
        .resideInAPackage("..platform.integration.copilot.audit..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.analytics..",
            "io.github.khaledshawki.eoc.operations..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.tenantaccess..",
            "org.springframework.ai..",
            "io.modelcontextprotocol..")
        .because("the audit decorator may compose only Copilot and Audit application contracts")
        .check(PLATFORM_CLASSES);
  }
}
