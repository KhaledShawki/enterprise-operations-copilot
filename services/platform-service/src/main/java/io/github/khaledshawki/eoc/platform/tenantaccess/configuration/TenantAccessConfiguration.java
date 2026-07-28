package io.github.khaledshawki.eoc.platform.tenantaccess.configuration;

import io.github.khaledshawki.eoc.tenantaccess.application.port.in.AssignTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.CreateTenantUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.GetTenantMembershipUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.in.ProvisionPlatformUserUseCase;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.PlatformUserRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantMembershipRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.port.out.TenantRepository;
import io.github.khaledshawki.eoc.tenantaccess.application.service.AssignTenantMembershipService;
import io.github.khaledshawki.eoc.tenantaccess.application.service.CreateTenantService;
import io.github.khaledshawki.eoc.tenantaccess.application.service.GetTenantMembershipService;
import io.github.khaledshawki.eoc.tenantaccess.application.service.ProvisionPlatformUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TenantAccessConfiguration {

  @Bean
  CreateTenantUseCase createTenantUseCase(TenantRepository tenantRepository) {
    return new CreateTenantService(tenantRepository);
  }

  @Bean
  ProvisionPlatformUserUseCase provisionPlatformUserUseCase(
      PlatformUserRepository platformUserRepository) {
    return new ProvisionPlatformUserService(platformUserRepository);
  }

  @Bean
  AssignTenantMembershipUseCase assignTenantMembershipUseCase(
      TenantRepository tenantRepository,
      PlatformUserRepository platformUserRepository,
      TenantMembershipRepository tenantMembershipRepository) {
    return new AssignTenantMembershipService(
        tenantRepository, platformUserRepository, tenantMembershipRepository);
  }

  @Bean
  GetTenantMembershipUseCase getTenantMembershipUseCase(
      TenantRepository tenantRepository, TenantMembershipRepository tenantMembershipRepository) {
    return new GetTenantMembershipService(tenantRepository, tenantMembershipRepository);
  }
}
