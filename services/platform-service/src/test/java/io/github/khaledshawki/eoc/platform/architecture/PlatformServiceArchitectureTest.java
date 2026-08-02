package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.BusinessDataSource;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

class PlatformServiceArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String TENANT_ACCESS_PACKAGE = "io.github.khaledshawki.eoc.tenantaccess";
  private static final String CONNECTOR_MANAGEMENT_PACKAGE =
      "io.github.khaledshawki.eoc.connectormanagement";
  private static final String INPUT_PORT_PACKAGE = TENANT_ACCESS_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE =
      TENANT_ACCESS_PACKAGE + ".application.port.out..";
  private static final String CONNECTOR_OUTPUT_PORT_PACKAGE =
      CONNECTOR_MANAGEMENT_PACKAGE + ".application.port.out..";
  private static final String CONNECTOR_INPUT_PORT_PACKAGE =
      CONNECTOR_MANAGEMENT_PACKAGE + ".application.port.in..";
  private static final String CONNECTOR_APPLICATION_SERVICE_PACKAGE =
      CONNECTOR_MANAGEMENT_PACKAGE + ".application.service..";
  private static final String APPLICATION_SERVICE_PACKAGE =
      TENANT_ACCESS_PACKAGE + ".application.service..";
  private static final String TENANT_WEB_ADAPTER_PACKAGE =
      PLATFORM_PACKAGE + ".tenantaccess.adapter.in.web..";
  private static final String TENANT_PERSISTENCE_ADAPTER_PACKAGE =
      PLATFORM_PACKAGE + ".tenantaccess.adapter.out.persistence..";
  private static final String CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.out.persistence..";
  private static final String CONNECTOR_DATA_SOURCE_ADAPTER_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.out.datasource..";
  private static final String CONNECTOR_WEB_ADAPTER_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.adapter.in.web..";
  private static final String CONNECTOR_CONFIGURATION_PACKAGE =
      PLATFORM_PACKAGE + ".connectormanagement.configuration..";
  private static final String PERSISTENCE_SUPPORT_PACKAGE = PLATFORM_PACKAGE + ".persistence..";
  private static final String TENANT_CONFIGURATION_PACKAGE =
      PLATFORM_PACKAGE + ".tenantaccess.configuration..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void tenantWebControllersUseInputPorts() {
    classes()
        .that()
        .resideInAPackage(TENANT_WEB_ADAPTER_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(INPUT_PORT_PACKAGE)
        .because("web controllers must invoke tenant use cases through input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void tenantWebAdaptersDoNotDependOnApplicationServicesOrOutputPorts() {
    noClasses()
        .that()
        .resideInAPackage(TENANT_WEB_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(APPLICATION_SERVICE_PACKAGE, OUTPUT_PORT_PACKAGE)
        .because("inbound adapters must not bypass input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void tenantWebAdaptersDoNotDependOnPersistenceOrConfiguration() {
    noClasses()
        .that()
        .resideInAPackage(TENANT_WEB_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(TENANT_PERSISTENCE_ADAPTER_PACKAGE, TENANT_CONFIGURATION_PACKAGE)
        .because("inbound adapters must remain independent of outbound adapters and wiring")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void tenantPersistenceAdaptersImplementOutputPorts() {
    classes()
        .that()
        .resideInAPackage(TENANT_PERSISTENCE_ADAPTER_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("PersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(OUTPUT_PORT_PACKAGE))
        .because("outbound adapters must implement application-owned output ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorPersistenceAdaptersImplementConnectorOutputPorts() {
    classes()
        .that()
        .resideInAPackage(CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("PersistenceAdapter")
        .should()
        .implement(JavaClass.Predicates.resideInAPackage(CONNECTOR_OUTPUT_PORT_PACKAGE))
        .because("connector persistence must implement connector-owned output ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorDataSourceAdaptersImplementTheBusinessDataSourcePort() {
    classes()
        .that()
        .resideInAPackage(CONNECTOR_DATA_SOURCE_ADAPTER_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("DataSourceAdapter")
        .should()
        .implement(BusinessDataSource.class)
        .because("external business data adapters must implement their exact connector-owned port")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorDataSourceAdaptersRemainIsolatedFromInboundPersistenceAndOtherContexts() {
    noClasses()
        .that()
        .resideInAPackage(CONNECTOR_DATA_SOURCE_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            CONNECTOR_WEB_ADAPTER_PACKAGE,
            CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE,
            TENANT_WEB_ADAPTER_PACKAGE,
            TENANT_PERSISTENCE_ADAPTER_PACKAGE,
            CONNECTOR_CONFIGURATION_PACKAGE,
            TENANT_ACCESS_PACKAGE + "..",
            "io.github.khaledshawki.eoc.operations..")
        .because(
            "external source adapters must not bypass ports or couple bounded contexts through"
                + " infrastructure")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorWebControllersUseConnectorInputPorts() {
    classes()
        .that()
        .resideInAPackage(CONNECTOR_WEB_ADAPTER_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CONNECTOR_INPUT_PORT_PACKAGE)
        .because("connector web controllers must invoke use cases through input ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorWebAdaptersDoNotBypassInputPorts() {
    noClasses()
        .that()
        .resideInAPackage(CONNECTOR_WEB_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONNECTOR_APPLICATION_SERVICE_PACKAGE, CONNECTOR_OUTPUT_PORT_PACKAGE)
        .because("connector inbound adapters must not depend on services or output ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorWebAdaptersDoNotDependOnPersistenceOrConfiguration() {
    noClasses()
        .that()
        .resideInAPackage(CONNECTOR_WEB_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_CONFIGURATION_PACKAGE)
        .because("connector inbound adapters must remain independent of wiring and persistence")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void persistenceFrameworksRemainInsidePersistenceAdapters() {
    noClasses()
        .that()
        .resideOutsideOfPackages(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE,
            CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE,
            PERSISTENCE_SUPPORT_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.persistence..", "org.hibernate..", "org.springframework.data..")
        .because("persistence technology must not leak into other layers or contexts")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void tenantPersistenceIsNotUsedByOtherContextsOrLayers() {
    noClasses()
        .that()
        .resideOutsideOfPackages(TENANT_PERSISTENCE_ADAPTER_PACKAGE, TENANT_CONFIGURATION_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(TENANT_PERSISTENCE_ADAPTER_PACKAGE)
        .because("other layers and contexts must collaborate through tenant ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void connectorPersistenceIsNotUsedByOtherContextsOrLayers() {
    noClasses()
        .that()
        .resideOutsideOfPackage(CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .because("other contexts must collaborate through connector ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void persistenceSupportIsUsedOnlyByPersistenceAdapters() {
    noClasses()
        .that()
        .resideOutsideOfPackages(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(PERSISTENCE_SUPPORT_PACKAGE)
        .because("shared persistence support must not leak into other layers")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void springDataRepositoriesRemainInternalToPersistence() {
    classes()
        .that()
        .areAssignableTo(Repository.class)
        .should()
        .resideInAnyPackage(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .andShould()
        .notBePublic()
        .because("framework repositories are persistence details, not application ports")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void jpaEntitiesRemainInternalToPersistence() {
    classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .should()
        .resideInAnyPackage(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .andShould()
        .notBePublic()
        .because("JPA entities are private persistence representations")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void persistenceRepresentationsDoNotLeakOutsidePersistence() {
    noClasses()
        .that()
        .resideOutsideOfPackages(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("JpaEntity")
        .because("contexts must collaborate through ports instead of persistence models")
        .check(PLATFORM_CLASSES);
  }

  @Test
  void persistenceAdaptersDoNotDependOnWebAdapters() {
    noClasses()
        .that()
        .resideInAnyPackage(
            TENANT_PERSISTENCE_ADAPTER_PACKAGE, CONNECTOR_PERSISTENCE_ADAPTER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..adapter.in.web..")
        .because("outbound adapters must remain independent of inbound adapters")
        .check(PLATFORM_CLASSES);
  }
}
