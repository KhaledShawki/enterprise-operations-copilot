package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessPartnerSourceMappingTest {

  private static final Instant FIRST_MODIFICATION = Instant.parse("2026-08-01T08:00:00Z");
  private static final Instant SECOND_MODIFICATION = Instant.parse("2026-08-01T09:00:00Z");
  private static final SourceRecordIdentity SOURCE_IDENTITY =
      SourceRecordIdentity.sourceRecordId("customer-100");

  @Test
  void shouldTreatSameOpaqueVersionAsDuplicate() {
    BusinessPartnerSourceMapping mapping = mapping("v1", Optional.of(FIRST_MODIFICATION));

    SourceRecordAcceptance acceptance =
        mapping.accept(new SourceRecordVersion("v1"), Optional.of(SECOND_MODIFICATION));

    assertEquals(SourceRecordAcceptance.DUPLICATE, acceptance);
    assertEquals(new SourceRecordVersion("v1"), mapping.sourceVersion());
    assertEquals(Optional.of(FIRST_MODIFICATION), mapping.sourceModifiedAt());
  }

  @Test
  void shouldAcceptDifferentVersionWithNewerTimestamp() {
    BusinessPartnerSourceMapping mapping = mapping("v1", Optional.of(FIRST_MODIFICATION));

    SourceRecordAcceptance acceptance =
        mapping.accept(new SourceRecordVersion("v2"), Optional.of(SECOND_MODIFICATION));

    assertEquals(SourceRecordAcceptance.ACCEPTED, acceptance);
    assertEquals(new SourceRecordVersion("v2"), mapping.sourceVersion());
    assertEquals(Optional.of(SECOND_MODIFICATION), mapping.sourceModifiedAt());
  }

  @Test
  void shouldIgnoreProvablyOlderVersionAsStaleWithoutMutatingCheckpoint() {
    BusinessPartnerSourceMapping mapping = mapping("v2", Optional.of(SECOND_MODIFICATION));

    SourceRecordAcceptance acceptance =
        mapping.accept(new SourceRecordVersion("v1"), Optional.of(FIRST_MODIFICATION));

    assertEquals(SourceRecordAcceptance.STALE, acceptance);
    assertEquals(new SourceRecordVersion("v2"), mapping.sourceVersion());
    assertEquals(Optional.of(SECOND_MODIFICATION), mapping.sourceModifiedAt());
  }

  @Test
  void shouldRejectDifferentVersionsAtTheSameTimestamp() {
    BusinessPartnerSourceMapping mapping = mapping("v1", Optional.of(FIRST_MODIFICATION));

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> mapping.accept(new SourceRecordVersion("v2"), Optional.of(FIRST_MODIFICATION)));
  }

  @Test
  void shouldRejectUnorderedVersionAfterTimestampedVersionWasAccepted() {
    BusinessPartnerSourceMapping mapping = mapping("v1", Optional.of(FIRST_MODIFICATION));

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> mapping.accept(new SourceRecordVersion("v2"), Optional.empty()));
  }

  @Test
  void shouldAcceptFirstTimestampWhenExistingSourceDidNotProvideOne() {
    BusinessPartnerSourceMapping mapping = mapping("v1", Optional.empty());

    SourceRecordAcceptance acceptance =
        mapping.accept(new SourceRecordVersion("v2"), Optional.of(SECOND_MODIFICATION));

    assertEquals(SourceRecordAcceptance.ACCEPTED, acceptance);
    assertEquals(Optional.of(SECOND_MODIFICATION), mapping.sourceModifiedAt());
  }

  @Test
  void shouldAcceptSequentialDifferentOpaqueVersionsWhenNeitherHasOrderingEvidence() {
    BusinessPartnerSourceMapping mapping = mapping("opaque-a", Optional.empty());

    SourceRecordAcceptance acceptance =
        mapping.accept(new SourceRecordVersion("opaque-b"), Optional.empty());

    assertEquals(SourceRecordAcceptance.ACCEPTED, acceptance);
    assertEquals(new SourceRecordVersion("opaque-b"), mapping.sourceVersion());
  }

  @Test
  void shouldValidateSourceIdentityAndVersion() {
    assertThrows(IllegalArgumentException.class, () -> SourceRecordIdentity.sourceRecordId(" "));
    assertThrows(
        IllegalArgumentException.class,
        () -> SourceRecordIdentity.canonicalRecordHash("not-a-sha256"));
    assertThrows(IllegalArgumentException.class, () -> new SourceRecordVersion(" "));

    SourceRecordIdentity hashIdentity = SourceRecordIdentity.canonicalRecordHash("A".repeat(64));

    assertEquals("a".repeat(64), hashIdentity.value());
  }

  private static BusinessPartnerSourceMapping mapping(
      String sourceVersion, Optional<Instant> sourceModifiedAt) {
    return BusinessPartnerSourceMapping.create(
        OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
        SourceSystemId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
        SOURCE_IDENTITY,
        BusinessPartnerId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
        new SourceRecordVersion(sourceVersion),
        sourceModifiedAt);
  }
}
