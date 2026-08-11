package io.github.khaledshawki.eoc.platform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.khaledshawki.eoc.operations.application.port.in.InspectOperationsOutboxUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.RecoverOperationsOutboxEventUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxInspectionRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsOutboxRecoveryRepository;
import org.junit.jupiter.api.Test;

class OperationsOutboxAdministrationArchitectureTest {

  private static final String PLATFORM_PACKAGE = "io.github.khaledshawki.eoc.platform";
  private static final String OPERATIONS_WEB_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.in.web..";
  private static final String OPERATIONS_PERSISTENCE_PACKAGE =
      PLATFORM_PACKAGE + ".operations.adapter.out.persistence..";

  private static final JavaClasses PLATFORM_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(PLATFORM_PACKAGE);

  @Test
  void administrationPersistenceImplementsOnlyOperationsOwnedOutputPorts() {
    classes()
        .that()
        .haveSimpleName("OperationsOutboxAdministrationPersistenceAdapter")
        .should()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .andShould()
        .implement(OperationsOutboxInspectionRepository.class)
        .andShould()
        .implement(OperationsOutboxRecoveryRepository.class)
        .check(PLATFORM_CLASSES);
  }

  @Test
  void administrationWebAdapterEntersThroughOperationsInputPorts() {
    classes()
        .that()
        .haveSimpleName("OperationsOutboxAdministrationController")
        .should()
        .resideInAPackage(OPERATIONS_WEB_PACKAGE)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(InspectOperationsOutboxUseCase.class)
        .andShould()
        .dependOnClassesThat()
        .areAssignableTo(RecoverOperationsOutboxEventUseCase.class)
        .check(PLATFORM_CLASSES);
  }

  @Test
  void administrationWebAdapterCannotBypassApplicationPorts() {
    noClasses()
        .that()
        .haveSimpleName("OperationsOutboxAdministrationController")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(OperationsOutboxInspectionRepository.class)
        .orShould()
        .dependOnClassesThat()
        .areAssignableTo(OperationsOutboxRecoveryRepository.class)
        .orShould()
        .dependOnClassesThat()
        .resideInAPackage(OPERATIONS_PERSISTENCE_PACKAGE)
        .check(PLATFORM_CLASSES);
  }
}
