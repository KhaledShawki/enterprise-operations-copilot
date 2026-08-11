package io.github.khaledshawki.eoc.connectormanagement.application.model.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectorDeadLetterRecordTest {

  @Test
  void fingerprintDistinguishesAbsentValuesFromLiteralSentinels() {
    ConnectorDeadLetterRecord absent = record(Optional.empty(), Optional.empty(), List.of());
    ConnectorDeadLetterRecord literal =
        record(Optional.of("<null>"), Optional.of("<null>"), List.of());

    assertNotEquals(absent.fingerprint(), literal.fingerprint());
    assertEquals(64, absent.fingerprint().length());
  }

  @Test
  void fingerprintIncludesHeaderOrderBecauseKafkaDuplicateHeaderOrderIsObservable() {
    ConnectorDeadLetterHeader first =
        ConnectorDeadLetterHeader.fromBytes("trace", "one".getBytes(StandardCharsets.UTF_8));
    ConnectorDeadLetterHeader second =
        ConnectorDeadLetterHeader.fromBytes("trace", "two".getBytes(StandardCharsets.UTF_8));

    assertNotEquals(
        record(Optional.of("key"), Optional.of("value"), List.of(first, second)).fingerprint(),
        record(Optional.of("key"), Optional.of("value"), List.of(second, first)).fingerprint());
  }

  private static ConnectorDeadLetterRecord record(
      Optional<String> key, Optional<String> value, List<ConnectorDeadLetterHeader> headers) {
    return new ConnectorDeadLetterRecord(
        new ConnectorDeadLetterReference(2, 19),
        "connector.events.dlt",
        key,
        value,
        "connector.events",
        2,
        11,
        Instant.parse("2026-08-10T12:00:00Z"),
        "invalid-connector-event-payload",
        false,
        "java.lang.IllegalArgumentException",
        Optional.of("invalid payload"),
        0,
        headers);
  }
}
