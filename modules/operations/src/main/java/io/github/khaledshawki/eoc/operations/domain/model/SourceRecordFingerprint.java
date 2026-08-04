package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 fingerprint of a canonical operational source-record payload. */
public record SourceRecordFingerprint(String value) {

  public static final int SHA_256_HEX_LENGTH = 64;

  private static final Pattern SHA_256_HEX_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

  public SourceRecordFingerprint {
    Objects.requireNonNull(value, "Source record fingerprint cannot be null");
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Source record fingerprint cannot be blank");
    }
    if (!SHA_256_HEX_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Source record fingerprint must be a SHA-256 hexadecimal value");
    }
  }

  public static SourceRecordFingerprint of(String value) {
    return new SourceRecordFingerprint(value);
  }
}
