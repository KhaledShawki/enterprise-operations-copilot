package io.github.khaledshawki.eoc.platform.operations.configuration;

import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.service.ImportBusinessPartnersService;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class OperationsConfiguration {

  @Bean
  ImportBusinessPartnersUseCase importBusinessPartnersUseCase(
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository sourceMappingRepository,
      BusinessPartnerImportReceiptRepository importReceiptRepository,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    ImportBusinessPartnersUseCase delegate =
        new ImportBusinessPartnersService(
            businessPartnerRepository, sourceMappingRepository, importReceiptRepository, clock);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return command -> {
      BusinessPartnerImportResult result =
          transactionTemplate.execute(status -> delegate.importPage(command));
      return Objects.requireNonNull(result, "Business partner import transaction returned null");
    };
  }
}
