package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record TenantRoleKey(String value) {

  public static final int MAX_LENGTH = 63;

  private static final Pattern VALID_FORMAT = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

  public TenantRoleKey {
    Objects.requireNonNull(value, "Tenant role key cannot be null");

    value = value.trim().toLowerCase(Locale.ROOT);

    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tenant role key cannot be empty");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tenant role key cannot be longer than " + MAX_LENGTH + " characters");
    }

    if (!VALID_FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Tenant role key has an invalid format");
    }
  }

  public static TenantRoleKey of(String value) {
    return new TenantRoleKey(value);
  }
}
