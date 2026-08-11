package io.github.khaledshawki.eoc.analytics.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessPartnerProjectionTest {

  @Test
  void canonicalizesTextAndRoles() {
    BusinessPartnerProjection projection =
        new BusinessPartnerProjection(
            AnalyticsTenantId.of(UUID.randomUUID()),
            UUID.randomUUID(),
            " C-100 ",
            " Acme AG ",
            new LinkedHashSet<>(Set.of("SUPPLIER", "CUSTOMER")),
            new ProjectionCursor(UUID.randomUUID(), 1, Instant.parse("2026-08-12T00:00:00Z")));

    assertEquals("C-100", projection.partnerNumber());
    assertEquals("Acme AG", projection.displayName());
    assertEquals(Set.of("CUSTOMER", "SUPPLIER"), projection.roles());
  }

  @Test
  void rejectsInvalidRoleCode() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BusinessPartnerProjection(
                AnalyticsTenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                "C-100",
                "Acme AG",
                Set.of("customer"),
                new ProjectionCursor(UUID.randomUUID(), 1, Instant.parse("2026-08-12T00:00:00Z"))));
  }

  @Test
  void rejectsEmptyRoles() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BusinessPartnerProjection(
                AnalyticsTenantId.of(UUID.randomUUID()),
                UUID.randomUUID(),
                "C-100",
                "Acme AG",
                Set.of(),
                new ProjectionCursor(UUID.randomUUID(), 1, Instant.parse("2026-08-12T00:00:00Z"))));
  }
}
