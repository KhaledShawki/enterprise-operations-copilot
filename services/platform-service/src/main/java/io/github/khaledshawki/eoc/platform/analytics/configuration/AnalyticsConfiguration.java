package io.github.khaledshawki.eoc.platform.analytics.configuration;

import io.github.khaledshawki.eoc.analytics.application.model.event.AnalyticsEventConsumptionResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ConsumeAnalyticsIntegrationEventUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.out.AnalyticsIntegrationEventInbox;
import io.github.khaledshawki.eoc.analytics.application.port.out.BusinessPartnerProjectionRepository;
import io.github.khaledshawki.eoc.analytics.application.port.out.InvoiceReceivableProjectionRepository;
import io.github.khaledshawki.eoc.analytics.application.service.ConsumeAnalyticsIntegrationEventService;
import io.github.khaledshawki.eoc.analytics.application.service.ProjectBusinessPartnerService;
import io.github.khaledshawki.eoc.analytics.application.service.ProjectInvoiceReceivableService;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {

  @Bean
  ProjectBusinessPartnerUseCase projectBusinessPartnerUseCase(
      BusinessPartnerProjectionRepository repository) {
    return new ProjectBusinessPartnerService(repository);
  }

  @Bean
  ProjectInvoiceReceivableUseCase projectInvoiceReceivableUseCase(
      InvoiceReceivableProjectionRepository repository) {
    return new ProjectInvoiceReceivableService(repository);
  }

  @Bean
  ConsumeAnalyticsIntegrationEventUseCase consumeAnalyticsIntegrationEventUseCase(
      AnalyticsIntegrationEventInbox inbox,
      ProjectBusinessPartnerUseCase businessPartnerProjector,
      ProjectInvoiceReceivableUseCase invoiceProjector,
      PlatformTransactionManager transactionManager) {
    ConsumeAnalyticsIntegrationEventUseCase delegate =
        new ConsumeAnalyticsIntegrationEventService(
            inbox, businessPartnerProjector, invoiceProjector);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return event -> {
      AnalyticsEventConsumptionResult result =
          transactionTemplate.execute(status -> delegate.consume(event));
      return Objects.requireNonNull(result, "Analytics event transaction returned null");
    };
  }
}
