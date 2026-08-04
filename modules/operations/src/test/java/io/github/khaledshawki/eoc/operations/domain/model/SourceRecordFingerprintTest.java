package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourceRecordFingerprintTest {

  private static final String LOWERCASE_FINGERPRINT = "a".repeat(64);

  @Test
  void shouldAcceptCanonicalLowercaseFingerprint() {
    assertEquals(LOWERCASE_FINGERPRINT, SourceRecordFingerprint.of(LOWERCASE_FINGERPRINT).value());
  }

  @Test
  void shouldTrimAndNormalizeUppercaseFingerprint() {
    assertEquals(
        LOWERCASE_FINGERPRINT, SourceRecordFingerprint.of(" " + "A".repeat(64) + " ").value());
  }

  @Test
  void shouldRejectMissingBlankMalformedAndWrongLengthFingerprints() {
    assertThrows(NullPointerException.class, () -> SourceRecordFingerprint.of(null));
    assertThrows(IllegalArgumentException.class, () -> SourceRecordFingerprint.of(" "));
    assertThrows(IllegalArgumentException.class, () -> SourceRecordFingerprint.of("a".repeat(63)));
    assertThrows(IllegalArgumentException.class, () -> SourceRecordFingerprint.of("a".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> SourceRecordFingerprint.of("g".repeat(64)));
  }
}
