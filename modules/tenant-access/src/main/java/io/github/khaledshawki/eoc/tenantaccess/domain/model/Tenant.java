package io.github.khaledshawki.eoc.tenantaccess.domain.model;

import java.util.Objects;

public final class Tenant {

  private final TenantId id;
  private TenantName name;
  private TenantStatus status;

  private Tenant(TenantId id, TenantName name, TenantStatus status) {
    if (id == null) {
      throw new IllegalArgumentException("Tenant id cannot be null");
    }

    if (name == null) {
      throw new IllegalArgumentException("Tenant name cannot be null");
    }

    if (status == null) {
      throw new IllegalArgumentException("Tenant status cannot be null");
    }

    this.id = id;
    this.name = name;
    this.status = status;
  }

  public static Tenant create(TenantName name) {
    return new Tenant(TenantId.generate(), name, TenantStatus.ACTIVE);
  }

  public static Tenant reconstitute(TenantId id, TenantName name, TenantStatus status) {
    return new Tenant(id, name, status);
  }

  public void rename(TenantName name) {
    if (name == null) {
      throw new IllegalArgumentException("Tenant name cannot be null");
    }

    this.name = name;
  }

  public void suspend() {
    if (Objects.equals(this.status, TenantStatus.SUSPENDED)) {
      throw new IllegalStateException("Tenant is already suspended");
    }

    this.status = TenantStatus.SUSPENDED;
  }

  public void activate() {
    if (Objects.equals(this.status, TenantStatus.ACTIVE)) {
      throw new IllegalStateException("Tenant is already active");
    }

    this.status = TenantStatus.ACTIVE;
  }

  public TenantId id() {
    return id;
  }

  public TenantName name() {
    return name;
  }

  public TenantStatus status() {
    return status;
  }
}
