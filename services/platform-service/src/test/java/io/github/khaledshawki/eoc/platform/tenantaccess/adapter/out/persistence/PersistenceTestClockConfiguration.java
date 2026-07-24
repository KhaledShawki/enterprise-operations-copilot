package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
class PersistenceTestClockConfiguration {

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.EPOCH, ZoneOffset.UTC);
  }
}
