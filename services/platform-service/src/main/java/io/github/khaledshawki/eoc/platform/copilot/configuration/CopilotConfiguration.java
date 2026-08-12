package io.github.khaledshawki.eoc.platform.copilot.configuration;

import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivableUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.GetReceivablesSummaryUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.in.ListReceivablesUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.in.ExecuteCopilotToolUseCase;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesAuthorizationPort;
import io.github.khaledshawki.eoc.copilot.application.port.out.CopilotReceivablesDataPort;
import io.github.khaledshawki.eoc.copilot.application.service.CopilotToolExecutorService;
import io.github.khaledshawki.eoc.platform.copilot.adapter.out.analytics.AnalyticsCopilotReceivablesAdapter;
import io.github.khaledshawki.eoc.platform.copilot.adapter.out.security.TenantAccessCopilotAuthorizationAdapter;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ResolveTenantAccessUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CopilotConfiguration {
  @Bean
  CopilotReceivablesDataPort copilotReceivablesDataPort(
      GetReceivableUseCase getReceivableUseCase,
      ListReceivablesUseCase listReceivablesUseCase,
      GetReceivablesSummaryUseCase getReceivablesSummaryUseCase) {
    return new AnalyticsCopilotReceivablesAdapter(
        getReceivableUseCase, listReceivablesUseCase, getReceivablesSummaryUseCase);
  }

  @Bean
  CopilotReceivablesAuthorizationPort copilotReceivablesAuthorizationPort(
      ResolveTenantAccessUseCase resolveTenantAccessUseCase) {
    return new TenantAccessCopilotAuthorizationAdapter(resolveTenantAccessUseCase);
  }

  @Bean
  ExecuteCopilotToolUseCase executeCopilotToolUseCase(
      CopilotReceivablesAuthorizationPort authorizationPort,
      CopilotReceivablesDataPort dataPort,
      Clock clock) {
    return new CopilotToolExecutorService(authorizationPort, dataPort, clock);
  }
}
