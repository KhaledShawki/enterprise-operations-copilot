package io.github.khaledshawki.eoc.analytics.domain.model;

import java.util.Objects;
import java.util.UUID;

public record AnalyticsTenantId(UUID value) {

  public AnalyticsTenantId {
    Objects.requireNonNull(value, "Analytics tenant id cannot be null");
  }

  public static AnalyticsTenantId of(UUID value) {
    return new AnalyticsTenantId(value);
  }
}
