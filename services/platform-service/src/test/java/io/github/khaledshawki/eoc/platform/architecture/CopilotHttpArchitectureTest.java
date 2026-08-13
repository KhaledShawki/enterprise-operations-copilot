package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CopilotHttpArchitectureTest {
  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.github.khaledshawki.eoc.platform");

  @Test
  void copilotHttpAdapterDoesNotBypassApplicationOrUseInfrastructureApis() {
    noClasses()
        .that()
        .resideInAPackage("..platform.copilot.adapter.in.web..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.github.khaledshawki.eoc.analytics..",
            "io.github.khaledshawki.eoc.operations..",
            "io.github.khaledshawki.eoc.connectormanagement..",
            "io.github.khaledshawki.eoc.tenantaccess..",
            "io.github.khaledshawki.eoc.audit..",
            "..platform.audit.adapter.out..",
            "..platform.copilot.adapter.out..",
            "org.springframework.ai..",
            "io.modelcontextprotocol..")
        .because(
            "the Copilot HTTP adapter may depend on Copilot application contracts and "
                + "Platform authentication only")
        .check(PLATFORM_CLASSES);
  }
}
