package io.github.khaledshawki.eoc.tenantaccess.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class TenantAccessArchitectureTest {

  private static final String TENANT_ACCESS_PACKAGE = "io.github.khaledshawki.eoc.tenantaccess";
  private static final String DOMAIN_PACKAGE = TENANT_ACCESS_PACKAGE + ".domain..";
  private static final String APPLICATION_PACKAGE = TENANT_ACCESS_PACKAGE + ".application..";
  private static final String INPUT_PORT_PACKAGE = TENANT_ACCESS_PACKAGE + ".application.port.in..";
  private static final String OUTPUT_PORT_PACKAGE =
      TENANT_ACCESS_PACKAGE + ".application.port.out..";

  private static final JavaClasses TENANT_ACCESS_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(TENANT_ACCESS_PACKAGE);

  @Test
  void domainDependsOnlyOnDomainTypesAndTheJdk() {
    classes()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(DOMAIN_PACKAGE, "java..")
        .because("the domain must remain independent of application and infrastructure code")
        .check(TENANT_ACCESS_CLASSES);
  }

  @Test
  void moduleDoesNotDependOnSpringOrPersistenceFrameworks() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..")
        .because("tenant-access is a framework-free domain and application module")
        .check(TENANT_ACCESS_CLASSES);
  }

  @Test
  void applicationDoesNotDependOnAdaptersOrConfiguration() {
    noClasses()
        .that()
        .resideInAPackage(APPLICATION_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            TENANT_ACCESS_PACKAGE + ".adapter..",
            TENANT_ACCESS_PACKAGE + ".configuration..",
            TENANT_ACCESS_PACKAGE + ".config..")
        .because("application code must depend on ports, not infrastructure")
        .check(TENANT_ACCESS_CLASSES);
  }

  @Test
  void configurationRemainsOutsideTheModule() {
    noClasses()
        .should()
        .resideInAnyPackage(
            TENANT_ACCESS_PACKAGE + ".configuration..", TENANT_ACCESS_PACKAGE + ".config..")
        .because("runtime wiring belongs to the platform service")
        .check(TENANT_ACCESS_CLASSES);
  }

  @Test
  void useCasesAreInputPortInterfaces() {
    classes()
        .that()
        .resideInAPackage(INPUT_PORT_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("UseCase")
        .should()
        .beInterfaces()
        .because("inbound adapters must call the application through input ports")
        .check(TENANT_ACCESS_CLASSES);
  }

  @Test
  void repositoriesAreOutputPortInterfaces() {
    classes()
        .that()
        .resideInAPackage(OUTPUT_PORT_PACKAGE)
        .and()
        .haveSimpleNameEndingWith("Repository")
        .should()
        .beInterfaces()
        .because("persistence contracts belong to the application boundary")
        .check(TENANT_ACCESS_CLASSES);
  }
}
