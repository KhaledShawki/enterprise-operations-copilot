package io.github.khaledshawki.eoc.platform.observability.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import java.util.Locale;
import java.util.Set;

final class MetricCardinalityGuard implements MeterFilter {

  private static final Set<String> FORBIDDEN_NORMALIZED_TAG_KEYS =
      Set.of(
          "aggregateid",
          "correlationid",
          "customerid",
          "eventid",
          "importrunid",
          "invoiceid",
          "paymentid",
          "requestid",
          "runid",
          "spanid",
          "tenantid",
          "traceid",
          "userid");

  @Override
  public MeterFilterReply accept(Meter.Id id) {
    boolean forbidden =
        id.getTags().stream()
            .map(tag -> normalize(tag.getKey()))
            .anyMatch(FORBIDDEN_NORMALIZED_TAG_KEYS::contains);
    return forbidden ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
  }

  private static String normalize(String tagKey) {
    return tagKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }
}
