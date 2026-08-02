package io.github.khaledshawki.eoc.connectormanagement.application.model.datasource;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identity of one record inside a source entity and connector. */
public record SourceIdentity(SourceEntity entity, Kind kind, String value) {

  public static final int MAX_VALUE_LENGTH = 255;
  public static final int SHA_256_HEX_LENGTH = 64;

  private static final Pattern SHA_256_HEX_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

  public SourceIdentity {
    Objects.requireNonNull(entity, "Source entity cannot be null");
    Objects.requireNonNull(kind, "Source identity kind cannot be null");
    value = SourceContractValidation.requiredText(value, "Source identity value", MAX_VALUE_LENGTH);
    if (kind == Kind.CANONICAL_RECORD_HASH) {
      value = value.toLowerCase(Locale.ROOT);
      if (!SHA_256_HEX_FORMAT.matcher(value).matches()) {
        throw new IllegalArgumentException(
            "Canonical source record hash must be a SHA-256 hexadecimal value");
      }
    }
  }

  public static SourceIdentity sourceRecordId(SourceEntity entity, String recordId) {
    return new SourceIdentity(entity, Kind.SOURCE_RECORD_ID, recordId);
  }

  public static SourceIdentity canonicalRecordHash(SourceEntity entity, String hash) {
    return new SourceIdentity(entity, Kind.CANONICAL_RECORD_HASH, hash);
  }

  public enum Kind {
    SOURCE_RECORD_ID,
    CANONICAL_RECORD_HASH
  }
}
