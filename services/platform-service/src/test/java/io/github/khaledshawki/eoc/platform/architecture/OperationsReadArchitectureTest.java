package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import org.junit.jupiter.api.Test;

class OperationsReadArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String OPERATIONS_PACKAGE = "io.github.khaledshawki.eoc.operations";
  private static final String TENANT_ACCESS_PACKAGE = "io.github.khaledshawki.eoc.tenantaccess";
  private static final String OPERATIONS_INPUT_PORT_PACKAGE =
      OPERATIONS_PACKAGE + ".application.port.in..";
  private static final String OPERATIONS_OUTPUT_PORT_PACKAGE =
      OPERATIONS_PACKAGE + ".application.port.out..";
  private static final String OPERATIONS_APPLICATION_SERVICE_PACKAGE =
      OPERATIONS_PACKAGE + ".application.service..";
  private static final String OPERATIONS_WEB_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.in.web..";
  private static final String OPERATIONS_PERSISTENCE_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.out.persistence..";
  private static final String OPERATIONS_CONFIGURATION_PACKAGE =
      PLATFORM_PACKAGE + ".operations.configuration..";
  private static final String OPERATIONS_TENANT_ACCESS_PACKAGE =
      PLATFORM_PACKAGE + ".integration.operations.tenantaccess..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void operationsWebControllersUseOperationsInputPorts() {
    classes()
        .that()
        .resideInAPackage(OPERATIONS_WEB_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(OPERATIONS_INPUT_PORT_PACKAGE)
        .because("Operations web controllers must invoke Operations input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void operationsWebAdaptersDoNotBypassInputPorts() {
    noClasses()
        .that()
        .resideInAPackage(OPERATIONS_WEB_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            OPERATIONS_APPLICATION_SERVICE_PACKAGE,
            OPERATIONS_OUTPUT_PORT_PACKAGE,
            OPERATIONS_PERSISTENCE_PACKAGE,
            OPERATIONS_CONFIGURATION_PACKAGE)
        .because("Operations web adapters must remain independent of services and infrastructure")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void operationsTenantAuthorizationImplementsTheOperationsOwnedPort() {
    classes()
        .that()
        .implement(OperationsAuthorizationPort.class)
        .should()
        .resideInAPackage(OPERATIONS_TENANT_ACCESS_PACKAGE)
        .because("tenant authorization translation belongs to an explicit integration adapter")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void operationsTenantAuthorizationDependsOnlyOnTenantInputPorts() {
    noClasses()
        .that()
        .resideInAPackage(OPERATIONS_TENANT_ACCESS_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            TENANT_ACCESS_PACKAGE + ".domain..",
            TENANT_ACCESS_PACKAGE + ".application.service..",
            TENANT_ACCESS_PACKAGE + ".application.port.out..",
            PLATFORM_PACKAGE + ".tenantaccess.adapter..")
        .because("Operations authorization may depend only on Tenant Access input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void invoiceQueryPersistenceImplementsAnOperationsOutputPort() {
    classes()
        .that()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("InvoiceQueryPersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(OPERATIONS_OUTPUT_PORT_PACKAGE))
        .check(PLATFORM_CLASSES);
  }

  @Test
  void paymentQueryPersistenceImplementsAnOperationsOutputPort() {
    classes()
        .that()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("PaymentQueryPersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(OPERATIONS_OUTPUT_PORT_PACKAGE))
        .check(PLATFORM_CLASSES);
  }

  @Test
  void receivableReconciliationEvidencePersistenceImplementsAnOperationsOutputPort() {
    classes()
        .that()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .and()
        .haveSimpleName("ReceivableReconciliationEvidencePersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(OPERATIONS_OUTPUT_PORT_PACKAGE))
        .check(PLATFORM_CLASSES);
  }
}
