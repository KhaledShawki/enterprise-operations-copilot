package io.github.khaledshawki.eoc.platform.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
class ObservabilityMetricsConfiguration {

  @Bean
  MeterFilter metricCardinalityGuard() {
    return new MetricCardinalityGuard();
  }

  @Bean
  EventPipelineMetrics eventPipelineMetrics(MeterRegistry meterRegistry) {
    return new EventPipelineMetrics(meterRegistry);
  }

  @Bean
  ConnectorImportMetrics connectorImportMetrics(MeterRegistry meterRegistry) {
    return new ConnectorImportMetrics(meterRegistry);
  }

  @Bean
  CopilotMetrics copilotMetrics(MeterRegistry meterRegistry) {
    return new CopilotMetrics(meterRegistry);
  }

  @Bean
  OutboxBacklogMetricsSampler outboxBacklogMetricsSampler(
      JdbcTemplate jdbcTemplate, Clock clock, EventPipelineMetrics metrics) {
    return new OutboxBacklogMetricsSampler(jdbcTemplate, clock, metrics);
  }
}
