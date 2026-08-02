package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessPartnerTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void shouldCreateImportedCustomerWithTenantAndCustomerRole() {
    BusinessPartner businessPartner =
        BusinessPartner.importCustomer(TENANT_ID, profile(" C-100 ", " Acme GmbH "));

    assertEquals(TENANT_ID, businessPartner.tenantId());
    assertEquals("C-100", businessPartner.profile().partnerNumber());
    assertEquals("Acme GmbH", businessPartner.profile().displayName());
    assertEquals(Set.of(BusinessPartnerRole.CUSTOMER), businessPartner.roles());
  }

  @Test
  void shouldSynchronizeCustomerProfileWithoutChangingIdentityOrTenant() {
    BusinessPartner businessPartner =
        BusinessPartner.importCustomer(TENANT_ID, profile("C-100", "Acme GmbH"));
    BusinessPartnerId id = businessPartner.id();

    businessPartner.synchronizeCustomer(
        new BusinessPartnerProfile("C-101", "Acme AG", Optional.of(" accounts@acme.example ")));

    assertEquals(id, businessPartner.id());
    assertEquals(TENANT_ID, businessPartner.tenantId());
    assertEquals("C-101", businessPartner.profile().partnerNumber());
    assertEquals("Acme AG", businessPartner.profile().displayName());
    assertEquals(Optional.of("accounts@acme.example"), businessPartner.profile().emailAddress());
    assertTrue(businessPartner.roles().contains(BusinessPartnerRole.CUSTOMER));
  }

  @Test
  void shouldKeepReconstitutedRolesDefensively() {
    Set<BusinessPartnerRole> roles = new HashSet<>();
    roles.add(BusinessPartnerRole.VENDOR);
    BusinessPartner businessPartner =
        BusinessPartner.reconstitute(
            BusinessPartnerId.generate(), TENANT_ID, profile("V-1", "Vendor"), roles);

    roles.add(BusinessPartnerRole.CUSTOMER);

    assertEquals(Set.of(BusinessPartnerRole.VENDOR), businessPartner.roles());
    assertThrows(
        UnsupportedOperationException.class,
        () -> businessPartner.roles().add(BusinessPartnerRole.CUSTOMER));
  }

  @Test
  void shouldRejectMissingRolesWhenReconstituting() {
    BusinessPartnerId id = BusinessPartnerId.generate();
    BusinessPartnerProfile profile = profile("C-1", "Customer");

    assertThrows(
        IllegalArgumentException.class,
        () -> BusinessPartner.reconstitute(id, TENANT_ID, profile, Set.of()));
    assertThrows(
        NullPointerException.class,
        () -> BusinessPartner.reconstitute(id, TENANT_ID, profile, null));
  }

  @Test
  void shouldValidateAndNormalizeImportedProfileFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BusinessPartnerProfile(" ", "Customer", Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BusinessPartnerProfile("C-1", " ", Optional.empty()));
    assertThrows(
        NullPointerException.class, () -> new BusinessPartnerProfile("C-1", "Customer", null));

    BusinessPartnerProfile profile =
        new BusinessPartnerProfile(" C-1 ", " Customer ", Optional.of(" "));

    assertEquals("C-1", profile.partnerNumber());
    assertEquals("Customer", profile.displayName());
    assertEquals(Optional.empty(), profile.emailAddress());
  }

  private static BusinessPartnerProfile profile(String number, String name) {
    return new BusinessPartnerProfile(number, name, Optional.empty());
  }
}
