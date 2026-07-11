package io.github.khaledshawki.eoc.tenantaccess.domain.model;

public record TenantName(String value) {

  public static final int MAX_LENGTH = 100;

  public TenantName {
    if (value == null) {
      throw new IllegalArgumentException("Tenant name cannot be null");
    }

    value = value.trim();

    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tenant name cannot be empty");
    }

    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tenant name cannot be longer than " + MAX_LENGTH + " characters");
    }
  }

  public static TenantName of(String value) {
    return new TenantName(value);
  }
}
