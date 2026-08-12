package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesAuthorizationPort;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesDataPort;
import org.junit.jupiter.api.Test;

class CopilotToolArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses classes =
      new ClassFileImporter().importPackages("io.github.khaledshawki.eoc.platform.copilot");

  @Test
  void analyticsBridgeUsesAnalyticsInputPortsButNotPersistenceOrServices() {
    noClasses()
        .that()
        .resideInAPackage("..platform.copilot.adapter.out.analytics..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..analytics.application.port.out..",
            "..analytics.application.service..",
            "..platform.analytics.adapter.out.persistence..")
        .check(classes);
  }

  @Test
  void authorizationBridgeUsesTenantAccessInputPortButNotPersistence() {
    noClasses()
        .that()
        .resideInAPackage("..platform.copilot.adapter.out.security..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..tenantaccess.application.port.out..",
            "..platform.tenantaccess.adapter.out.persistence..")
        .check(classes);
  }

  @Test
  void platformAdaptersImplementCopilotOutputPorts() {
    classes()
        .that()
        .haveSimpleName("AnalyticsCopilotReceivablesAdapter")
        .should()
        .implement(CopilotReceivablesDataPort.class)
        .check(classes);
    classes()
        .that()
        .haveSimpleName("TenantAccessCopilotAuthorizationAdapter")
        .should()
        .implement(CopilotReceivablesAuthorizationPort.class)
        .check(classes);
  }
}
