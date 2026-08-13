package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class CopilotMcpArchitectureTest {

  private static final JavaClasses PLATFORM_COPILOT_CLASSES =
      new ClassFileImporter().importPackages("io.github.khaledshawki.eoc.platform.copilot");

  @Test
  void mcpInboundAdapterCannotBypassCopilotApplicationBoundary() {
    noClasses()
        .that()
        .resideInAPackage("..platform.copilot.adapter.in.mcp..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..copilot.application.port.out..",
            "..analytics..",
            "..tenantaccess..",
            "..connectormanagement..",
            "..operations..",
            "..adapter.out.persistence..")
        .because("MCP is an inbound adapter and must delegate through Copilot input ports")
        .check(PLATFORM_COPILOT_CLASSES);
  }
}
