package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable source-owned identity. It does not expose connector implementation types. */
public record SourceRecordIdentity(Kind kind, String value) {

  public static final int MAX_VALUE_LENGTH = 255;
  public static final int SHA_256_HEX_LENGTH = 64;

  private static final Pattern SHA_256_HEX_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

  public SourceRecordIdentity {
    Objects.requireNonNull(kind, "Source record identity kind cannot be null");
    value = requiredText(value, "Source record identity", MAX_VALUE_LENGTH);
    if (kind == Kind.CANONICAL_RECORD_HASH) {
      value = value.toLowerCase(Locale.ROOT);
      if (!SHA_256_HEX_FORMAT.matcher(value).matches()) {
        throw new IllegalArgumentException(
            "Canonical source record hash must be a SHA-256 hexadecimal value");
      }
    }
  }

  public static SourceRecordIdentity sourceRecordId(String value) {
    return new SourceRecordIdentity(Kind.SOURCE_RECORD_ID, value);
  }

  public static SourceRecordIdentity canonicalRecordHash(String value) {
    return new SourceRecordIdentity(Kind.CANONICAL_RECORD_HASH, value);
  }

  private static String requiredText(String value, String field, int maxLength) {
    Objects.requireNonNull(value, field + " cannot be null");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(field + " cannot be blank");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
    }
    return normalized;
  }

  public enum Kind {
    SOURCE_RECORD_ID,
    CANONICAL_RECORD_HASH
  }
}
