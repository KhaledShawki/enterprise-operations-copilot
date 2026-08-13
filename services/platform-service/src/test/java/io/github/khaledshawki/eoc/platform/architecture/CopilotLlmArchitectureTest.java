package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class CopilotLlmArchitectureTest {

  private static final JavaClasses PLATFORM_COPILOT_CLASSES =
      new ClassFileImporter().importPackages("io.github.khaledshawki.eoc.platform.copilot");

  @Test
  void llmOutputAdapterCannotBypassCopilotOrchestrationOrBusinessBoundaries() {
    noClasses()
        .that()
        .resideInAPackage("..platform.copilot.adapter.out.llm..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..copilot.application.port.in..",
            "..analytics..",
            "..tenantaccess..",
            "..connectormanagement..",
            "..operations..",
            "..adapter.out.persistence..",
            "io.modelcontextprotocol..")
        .because(
            "the model adapter may translate model protocol only; orchestration executes approved Copilot input ports")
        .check(PLATFORM_COPILOT_CLASSES);
  }
}
