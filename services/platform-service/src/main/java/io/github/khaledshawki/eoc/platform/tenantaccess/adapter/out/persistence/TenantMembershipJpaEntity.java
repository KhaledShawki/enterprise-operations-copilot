package io.github.khaledshawki.eoc.platform.tenantaccess.adapter.out.persistence;

import io.github.khaledshawki.eoc.tenantaccess.domain.model.TenantMembershipStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships")
class TenantMembershipJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "platform_user_id", nullable = false, updatable = false)
  private UUID platformUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private TenantMembershipStatus status;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "tenant_membership_roles",
      joinColumns = @JoinColumn(name = "tenant_membership_id", nullable = false))
  @Column(name = "role_key", nullable = false, length = 63)
  private Set<String> roleKeys = new LinkedHashSet<>();

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantMembershipJpaEntity() {}

  TenantMembershipJpaEntity(
      UUID id,
      UUID tenantId,
      UUID platformUserId,
      TenantMembershipStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this(id, tenantId, platformUserId, status, Set.of(), createdAt, updatedAt);
  }

  TenantMembershipJpaEntity(
      UUID id,
      UUID tenantId,
      UUID platformUserId,
      TenantMembershipStatus status,
      Set<String> roleKeys,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.platformUserId = platformUserId;
    this.status = status;
    this.roleKeys = copyRoleKeys(roleKeys);
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  void updateMutableState(TenantMembershipStatus status, Instant updatedAt) {
    this.status = Objects.requireNonNull(status, "Tenant membership status cannot be null");

    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
  }

  void replaceRoleKeys(Set<String> roleKeys, Instant updatedAt) {
    Set<String> replacementRoleKeys = copyRoleKeys(roleKeys);

    this.roleKeys.clear();
    this.roleKeys.addAll(replacementRoleKeys);

    this.updatedAt = Objects.requireNonNull(updatedAt, "Updated at cannot be null");
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  UUID getPlatformUserId() {
    return platformUserId;
  }

  TenantMembershipStatus getStatus() {
    return status;
  }

  Set<String> getRoleKeys() {
    return Collections.unmodifiableSet(roleKeys);
  }

  long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  private static Set<String> copyRoleKeys(Set<String> roleKeys) {
    Objects.requireNonNull(roleKeys, "Tenant membership role keys cannot be null");

    if (roleKeys.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Tenant membership role keys cannot contain null values");
    }

    return new LinkedHashSet<>(roleKeys);
  }
}
