package io.github.khaledshawki.eoc.platform.audit.configuration;

import io.github.khaledshawki.eoc.audit.application.port.in.RecordCopilotExecutionAuditUseCase;
import io.github.khaledshawki.eoc.audit.application.port.out.AppendCopilotExecutionAuditEventPort;
import io.github.khaledshawki.eoc.audit.application.service.CopilotExecutionAuditService;
import io.github.khaledshawki.eoc.platform.audit.adapter.out.persistence.CopilotExecutionAuditPersistenceAdapter;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class AuditConfiguration {

  @Bean
  AppendCopilotExecutionAuditEventPort appendCopilotExecutionAuditEventPort(
      JdbcTemplate jdbcTemplate) {
    return new CopilotExecutionAuditPersistenceAdapter(jdbcTemplate);
  }

  @Bean
  RecordCopilotExecutionAuditUseCase recordCopilotExecutionAuditUseCase(
      AppendCopilotExecutionAuditEventPort appendPort, Clock clock) {
    return new CopilotExecutionAuditService(appendPort, clock, UUID::randomUUID);
  }
}
