package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerId;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessPartnerPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");
  private static final BusinessPartnerId BUSINESS_PARTNER_ID =
      BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

  private final BusinessPartnerPersistenceMapper mapper = new BusinessPartnerPersistenceMapper();

  @Test
  void shouldMapBusinessPartnerToInternalJpaEntity() {
    BusinessPartnerJpaEntity entity = mapper.toEntity(businessPartner(), NOW);

    assertAll(
        () -> assertEquals(BUSINESS_PARTNER_ID.value(), entity.getId()),
        () -> assertEquals(TENANT_ID.value(), entity.getTenantId()),
        () -> assertEquals("C-100", entity.getPartnerNumber()),
        () -> assertEquals("Acme GmbH", entity.getDisplayName()),
        () -> assertEquals("finance@acme.example", entity.getEmailAddress()),
        () -> assertEquals(Set.of(BusinessPartnerRole.CUSTOMER), entity.getRoles()),
        () -> assertEquals(NOW, entity.getCreatedAt()),
        () -> assertEquals(NOW, entity.getUpdatedAt()));
  }

  @Test
  void shouldRoundTripBusinessPartnerWithoutLosingRolesOrOptionalEmail() {
    BusinessPartnerJpaEntity entity = mapper.toEntity(businessPartner(), NOW);

    BusinessPartner restored = mapper.toDomain(entity);

    assertEquals(BUSINESS_PARTNER_ID, restored.id());
    assertEquals(TENANT_ID, restored.tenantId());
    assertEquals(businessPartner().profile(), restored.profile());
    assertEquals(Set.of(BusinessPartnerRole.CUSTOMER), restored.roles());
  }

  @Test
  void shouldUpdateOnlyMutableState() {
    BusinessPartnerJpaEntity entity = mapper.toEntity(businessPartner(), NOW);
    Instant updatedAt = NOW.plusSeconds(60);
    BusinessPartner updated =
        BusinessPartner.reconstitute(
            BUSINESS_PARTNER_ID,
            TENANT_ID,
            new BusinessPartnerProfile("C-101", "Acme AG", Optional.empty()),
            Set.of(BusinessPartnerRole.CUSTOMER, BusinessPartnerRole.VENDOR));

    BusinessPartnerJpaEntity result = mapper.updateEntity(updated, entity, updatedAt);

    assertSame(entity, result);
    assertAll(
        () -> assertEquals(BUSINESS_PARTNER_ID.value(), result.getId()),
        () -> assertEquals(TENANT_ID.value(), result.getTenantId()),
        () -> assertEquals("C-101", result.getPartnerNumber()),
        () -> assertEquals("Acme AG", result.getDisplayName()),
        () -> assertEquals(null, result.getEmailAddress()),
        () ->
            assertEquals(
                Set.of(BusinessPartnerRole.CUSTOMER, BusinessPartnerRole.VENDOR),
                result.getRoles()),
        () -> assertEquals(NOW, result.getCreatedAt()),
        () -> assertEquals(updatedAt, result.getUpdatedAt()));
  }

  @Test
  void shouldRejectImmutableIdentityMismatchesAndNullInputs() {
    BusinessPartnerJpaEntity entity = mapper.toEntity(businessPartner(), NOW);
    BusinessPartner differentTenant =
        BusinessPartner.reconstitute(
            BUSINESS_PARTNER_ID,
            OperationsTenantId.of(UUID.randomUUID()),
            businessPartner().profile(),
            businessPartner().roles());
    BusinessPartner differentId =
        BusinessPartner.reconstitute(
            BusinessPartnerId.generate(),
            TENANT_ID,
            businessPartner().profile(),
            businessPartner().roles());

    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(differentTenant, entity, NOW));
    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(differentId, entity, NOW));
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null, NOW));
    assertThrows(NullPointerException.class, () -> mapper.toEntity(businessPartner(), null));
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
  }

  private static BusinessPartner businessPartner() {
    return BusinessPartner.reconstitute(
        BUSINESS_PARTNER_ID,
        TENANT_ID,
        new BusinessPartnerProfile("C-100", "Acme GmbH", Optional.of("finance@acme.example")),
        Set.of(BusinessPartnerRole.CUSTOMER));
  }
}
