package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

final class MutableClock extends Clock {

  private Instant instant;
  private final ZoneId zone;

  MutableClock(Instant instant, ZoneId zone) {
    this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
    this.zone = Objects.requireNonNull(zone, "Zone cannot be null");
  }

  void setInstant(Instant instant) {
    this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(instant, zone);
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
