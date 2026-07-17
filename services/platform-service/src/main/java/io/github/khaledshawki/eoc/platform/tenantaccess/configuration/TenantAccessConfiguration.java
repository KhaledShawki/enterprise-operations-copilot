package io.github.khaledshawki.eoc.platform.tenantaccess.configuration;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.service.CreateTenantService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TenantAccessConfiguration {

  @Bean
  CreateTenantUseCase createTenantUseCase(TenantRepository tenantRepository) {
    return new CreateTenantService(tenantRepository);
  }
}
