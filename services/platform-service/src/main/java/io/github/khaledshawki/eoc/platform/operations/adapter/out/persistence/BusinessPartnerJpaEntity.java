package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "operations_business_partners",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_operations_business_partners_id_tenant",
            columnNames = {"id", "tenant_id"}))
class BusinessPartnerJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(
      name = "partner_number",
      nullable = false,
      length = BusinessPartnerProfile.MAX_PARTNER_NUMBER_LENGTH)
  private String partnerNumber;

  @Column(
      name = "display_name",
      nullable = false,
      length = BusinessPartnerProfile.MAX_DISPLAY_NAME_LENGTH)
  private String displayName;

  @Column(name = "email_address", length = BusinessPartnerProfile.MAX_EMAIL_ADDRESS_LENGTH)
  private String emailAddress;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "operations_business_partner_roles",
      joinColumns = {
        @JoinColumn(name = "business_partner_id", referencedColumnName = "id"),
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id")
      })
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private Set<BusinessPartnerRole> roles = EnumSet.noneOf(BusinessPartnerRole.class);

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BusinessPartnerJpaEntity() {}

  BusinessPartnerJpaEntity(
      UUID id,
      UUID tenantId,
      String partnerNumber,
      String displayName,
      String emailAddress,
      Set<BusinessPartnerRole> roles,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Business partner id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Business partner tenant id cannot be null");
    this.partnerNumber =
        Objects.requireNonNull(partnerNumber, "Business partner number cannot be null");
    this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
    this.emailAddress = emailAddress;
    replaceRoles(roles);
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateMutableState(
      String partnerNumber,
      String displayName,
      String emailAddress,
      Set<BusinessPartnerRole> roles,
      Instant updatedAt) {
    this.partnerNumber =
        Objects.requireNonNull(partnerNumber, "Business partner number cannot be null");
    this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
    this.emailAddress = emailAddress;
    replaceRoles(roles);
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  private void replaceRoles(Set<BusinessPartnerRole> roles) {
    Objects.requireNonNull(roles, "Business partner roles cannot be null");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("Business partner roles cannot be empty");
    }
    if (roles.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Business partner roles cannot contain null");
    }
    EnumSet<BusinessPartnerRole> replacement = EnumSet.copyOf(roles);
    if (!this.roles.equals(replacement)) {
      this.roles.clear();
      this.roles.addAll(replacement);
    }
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  String getPartnerNumber() {
    return partnerNumber;
  }

  String getDisplayName() {
    return displayName;
  }

  String getEmailAddress() {
    return emailAddress;
  }

  Set<BusinessPartnerRole> getRoles() {
    return Collections.unmodifiableSet(roles);
  }

  Long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
