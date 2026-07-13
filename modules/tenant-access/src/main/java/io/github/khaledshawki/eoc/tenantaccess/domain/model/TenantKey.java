package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TenantKey(String value) {

  public static final int MAX_LENGTH = 63;

  private static final Pattern VALID_FORMAT = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public TenantKey {

    Objects.requireNonNull(value, "Tenant key cannot be null");

    value = value.trim().toLowerCase(Locale.ROOT);

    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tenant key cannot be empty");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tenant key cannot be longer than " + MAX_LENGTH + " characters");
    }

    if (!VALID_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Tenant key has an invalid format");
    }
  }

  public static TenantKey of(String value) {
    return new TenantKey(value);
  }
}
