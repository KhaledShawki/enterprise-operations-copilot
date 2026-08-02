package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OperationsPersistenceConfiguration {

  @Bean
  BusinessPartnerPersistenceMapper businessPartnerPersistenceMapper() {
    return new BusinessPartnerPersistenceMapper();
  }

  @Bean
  BusinessPartnerSourceMappingPersistenceMapper businessPartnerSourceMappingPersistenceMapper() {
    return new BusinessPartnerSourceMappingPersistenceMapper();
  }
}
