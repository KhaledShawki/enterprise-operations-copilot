package io.github.khaledshawki.eoc.platform.observability.metrics;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MetricCardinalityGuardTest {

  @Test
  void rejectsIdentifierLikeTagsAcrossCommonNamingStyles() {
    SimpleMeterRegistry registry = registryWithGuard();

    Counter.builder("eoc.test.tenant").tag("tenantId", "tenant-1").register(registry).increment();
    Counter.builder("eoc.test.event").tag("event_id", "event-1").register(registry).increment();
    Counter.builder("eoc.test.run").tag("run-id", "run-1").register(registry).increment();
    Counter.builder("eoc.test.trace").tag("traceId", "trace-1").register(registry).increment();

    assertNull(registry.find("eoc.test.tenant").counter());
    assertNull(registry.find("eoc.test.event").counter());
    assertNull(registry.find("eoc.test.run").counter());
    assertNull(registry.find("eoc.test.trace").counter());
  }

  @Test
  void permitsBoundedOperationalTags() {
    SimpleMeterRegistry registry = registryWithGuard();

    Counter.builder("eoc.test.pipeline")
        .tag("pipeline", "operations")
        .tag("outcome", "success")
        .register(registry)
        .increment();

    assertNotNull(registry.find("eoc.test.pipeline").counter());
  }

  private static SimpleMeterRegistry registryWithGuard() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    registry.config().meterFilter(new MetricCardinalityGuard());
    return registry;
  }
}
