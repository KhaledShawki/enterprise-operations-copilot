package io.github.khaledshawki.eoc.operations.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class BusinessPartner {

  private final BusinessPartnerId id;
  private final OperationsTenantId tenantId;
  private BusinessPartnerProfile profile;
  private final EnumSet<BusinessPartnerRole> roles;

  private BusinessPartner(
      BusinessPartnerId id,
      OperationsTenantId tenantId,
      BusinessPartnerProfile profile,
      Set<BusinessPartnerRole> roles) {
    this.id = Objects.requireNonNull(id, "Business partner id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Business partner tenant id cannot be null");
    this.profile = Objects.requireNonNull(profile, "Business partner profile cannot be null");
    Objects.requireNonNull(roles, "Business partner roles cannot be null");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("Business partner must have at least one role");
    }
    if (roles.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("Business partner roles cannot contain null");
    }
    this.roles = EnumSet.copyOf(roles);
  }

  public static BusinessPartner importCustomer(
      OperationsTenantId tenantId, BusinessPartnerProfile profile) {
    return new BusinessPartner(
        BusinessPartnerId.generate(), tenantId, profile, EnumSet.of(BusinessPartnerRole.CUSTOMER));
  }

  public static BusinessPartner reconstitute(
      BusinessPartnerId id,
      OperationsTenantId tenantId,
      BusinessPartnerProfile profile,
      Set<BusinessPartnerRole> roles) {
    return new BusinessPartner(id, tenantId, profile, roles);
  }

  public void synchronizeCustomer(BusinessPartnerProfile profile) {
    this.profile = Objects.requireNonNull(profile, "Business partner profile cannot be null");
    roles.add(BusinessPartnerRole.CUSTOMER);
  }

  public BusinessPartnerId id() {
    return id;
  }

  public OperationsTenantId tenantId() {
    return tenantId;
  }

  public BusinessPartnerProfile profile() {
    return profile;
  }

  public Set<BusinessPartnerRole> roles() {
    return Collections.unmodifiableSet(roles);
  }
}
