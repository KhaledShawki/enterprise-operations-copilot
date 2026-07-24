package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TenantPersistenceConfiguration {

  @Bean
  TenantPersistenceMapper tenantPersistenceMapper() {
    return new TenantPersistenceMapper();
  }

  @Bean
  PlatformUserPersistenceMapper platformUserPersistenceMapper() {
    return new PlatformUserPersistenceMapper();
  }

  @Bean
  TenantMembershipPersistenceMapper tenantMembershipPersistenceMapper() {
    return new TenantMembershipPersistenceMapper();
  }
}
