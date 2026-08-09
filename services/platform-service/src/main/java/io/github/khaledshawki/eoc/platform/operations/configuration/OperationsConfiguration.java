package io.github.khaledshawki.eoc.platform.operations.configuration;

import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.GetInvoiceUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.GetPaymentUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ListInvoicesUseCase;
import io.github.khaledshawki.eoc.operations.application.port.in.ListPaymentsUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceQueryRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.InvoiceSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.OperationsAuthorizationPort;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportReceiptRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentImportUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentQueryRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.PaymentSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementMutationUnitOfWork;
import io.github.khaledshawki.eoc.operations.application.port.out.ReceivableSettlementRepository;
import io.github.khaledshawki.eoc.operations.application.service.GetInvoiceService;
import io.github.khaledshawki.eoc.operations.application.service.GetPaymentService;
import io.github.khaledshawki.eoc.operations.application.service.ImportBusinessPartnersService;
import io.github.khaledshawki.eoc.operations.application.service.ImportInvoicesService;
import io.github.khaledshawki.eoc.operations.application.service.ImportPaymentsService;
import io.github.khaledshawki.eoc.operations.application.service.ListInvoicesService;
import io.github.khaledshawki.eoc.operations.application.service.ListPaymentsService;
import io.github.khaledshawki.eoc.operations.application.service.ReceivableSettlementService;
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

  @Bean
  ImportInvoicesUseCase importInvoicesUseCase(
      InvoiceRepository invoiceRepository,
      InvoiceSourceMappingRepository sourceMappingRepository,
      InvoiceImportReceiptRepository importReceiptRepository,
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository,
      InvoiceImportUnitOfWork unitOfWork,
      Clock clock) {
    return new ImportInvoicesService(
        invoiceRepository,
        sourceMappingRepository,
        importReceiptRepository,
        businessPartnerRepository,
        businessPartnerSourceMappingRepository,
        unitOfWork,
        clock);
  }

  @Bean
  ImportPaymentsUseCase importPaymentsUseCase(
      PaymentRepository paymentRepository,
      PaymentSourceMappingRepository sourceMappingRepository,
      PaymentImportReceiptRepository importReceiptRepository,
      BusinessPartnerRepository businessPartnerRepository,
      BusinessPartnerSourceMappingRepository businessPartnerSourceMappingRepository,
      PaymentImportUnitOfWork unitOfWork,
      Clock clock) {
    return new ImportPaymentsService(
        paymentRepository,
        sourceMappingRepository,
        importReceiptRepository,
        businessPartnerRepository,
        businessPartnerSourceMappingRepository,
        unitOfWork,
        clock);
  }

  @Bean
  ReceivableSettlementService receivableSettlementService(
      PaymentRepository paymentRepository,
      InvoiceRepository invoiceRepository,
      ReceivableSettlementRepository settlementRepository,
      ReceivableSettlementMutationUnitOfWork unitOfWork,
      OperationsAuthorizationPort operationsAuthorizationPort) {
    return new ReceivableSettlementService(
        paymentRepository,
        invoiceRepository,
        settlementRepository,
        unitOfWork,
        operationsAuthorizationPort);
  }

  @Bean
  GetInvoiceUseCase getInvoiceUseCase(
      InvoiceRepository invoiceRepository,
      OperationsAuthorizationPort operationsAuthorizationPort) {
    return new GetInvoiceService(invoiceRepository, operationsAuthorizationPort);
  }

  @Bean
  ListInvoicesUseCase listInvoicesUseCase(
      InvoiceQueryRepository invoiceQueryRepository,
      OperationsAuthorizationPort operationsAuthorizationPort) {
    return new ListInvoicesService(invoiceQueryRepository, operationsAuthorizationPort);
  }

  @Bean
  GetPaymentUseCase getPaymentUseCase(
      PaymentRepository paymentRepository,
      OperationsAuthorizationPort operationsAuthorizationPort) {
    return new GetPaymentService(paymentRepository, operationsAuthorizationPort);
  }

  @Bean
  ListPaymentsUseCase listPaymentsUseCase(
      PaymentQueryRepository paymentQueryRepository,
      OperationsAuthorizationPort operationsAuthorizationPort) {
    return new ListPaymentsService(paymentQueryRepository, operationsAuthorizationPort);
  }
}
