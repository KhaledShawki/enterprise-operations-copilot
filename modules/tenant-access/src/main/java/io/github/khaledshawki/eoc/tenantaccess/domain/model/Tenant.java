package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public final class Tenant {

  private final TenantId id;
  private final TenantKey key;
  private TenantName name;
  private TenantStatus status;

  private Tenant(TenantId id, TenantKey key, TenantName name, TenantStatus status) {
    this.id = Objects.requireNonNull(id, "Tenant ID cannot be null");
    this.key = Objects.requireNonNull(key, "Tenant key cannot be null");
    this.name = Objects.requireNonNull(name, "Tenant name cannot be null");
    this.status = Objects.requireNonNull(status, "Tenant status cannot be null");
  }

  public static Tenant create(TenantKey tenantKey, TenantName name) {
    return new Tenant(TenantId.generate(), tenantKey, name, TenantStatus.ACTIVE);
  }

  public static Tenant reconstitute(
      TenantId id, TenantKey key, TenantName name, TenantStatus status) {
    return new Tenant(id, key, name, status);
  }

  public void rename(TenantName name) {
    this.name = Objects.requireNonNull(name, "Tenant name cannot be null");
  }

  public void suspend() {
    if (this.status == TenantStatus.SUSPENDED) {
      throw new IllegalStateException("Tenant is already suspended");
    }

    this.status = TenantStatus.SUSPENDED;
  }

  public void activate() {
    if (this.status == TenantStatus.ACTIVE) {
      throw new IllegalStateException("Tenant is already active");
    }

    this.status = TenantStatus.ACTIVE;
  }

  public TenantId id() {
    return id;
  }

  public TenantKey key() {
    return key;
  }

  public TenantName name() {
    return name;
  }

  public TenantStatus status() {
    return status;
  }
}
