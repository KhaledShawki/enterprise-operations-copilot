package io.github.khaledshawki.eoc.operations.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceSourceMappingTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final SourceSystemId SOURCE_SYSTEM_ID =
      SourceSystemId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final SourceRecordIdentity SOURCE_IDENTITY =
      SourceRecordIdentity.sourceRecordId("invoice-100");
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000100"));
  private static final SourceRecordVersion VERSION_1 = new SourceRecordVersion("v1");
  private static final SourceRecordVersion VERSION_2 = new SourceRecordVersion("v2");
  private static final Instant SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");
  private static final SourceRecordFingerprint FINGERPRINT_1 = fingerprint('a');
  private static final SourceRecordFingerprint FINGERPRINT_2 = fingerprint('b');

  @Test
  void shouldCreateAndReconstituteMappingWithCompleteEvidence() {
    InvoiceSourceMapping created = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
    InvoiceSourceMapping reconstituted =
        InvoiceSourceMapping.reconstitute(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SOURCE_IDENTITY,
            INVOICE_ID,
            VERSION_1,
            Optional.of(SOURCE_TIME),
            FINGERPRINT_1);

    assertMapping(created, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
    assertMapping(reconstituted, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
  }

  @Test
  void shouldRejectMissingMappingEvidence() {
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                null,
                SOURCE_SYSTEM_ID,
                SOURCE_IDENTITY,
                INVOICE_ID,
                VERSION_1,
                Optional.of(SOURCE_TIME),
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                null,
                SOURCE_IDENTITY,
                INVOICE_ID,
                VERSION_1,
                Optional.of(SOURCE_TIME),
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                null,
                INVOICE_ID,
                VERSION_1,
                Optional.of(SOURCE_TIME),
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SOURCE_IDENTITY,
                null,
                VERSION_1,
                Optional.of(SOURCE_TIME),
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SOURCE_IDENTITY,
                INVOICE_ID,
                null,
                Optional.of(SOURCE_TIME),
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SOURCE_IDENTITY,
                INVOICE_ID,
                VERSION_1,
                null,
                FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () ->
            InvoiceSourceMapping.create(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SOURCE_IDENTITY,
                INVOICE_ID,
                VERSION_1,
                Optional.of(SOURCE_TIME),
                null));
  }

  @Test
  void shouldReturnCurrentMappingForExactReplay() {
    InvoiceSourceMapping mapping = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    InvoiceSourceRecordDecision decision =
        mapping.evaluate(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    assertEquals(SourceRecordAcceptance.DUPLICATE, decision.acceptance());
    assertSame(mapping, decision.resultingMapping());
  }

  @Test
  void shouldRejectEqualVersionWithChangedPayloadOrTimestamp() {
    InvoiceSourceMapping mapping = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () -> mapping.evaluate(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_2));
    assertThrows(
        ConflictingSourceRecordReplayException.class,
        () -> mapping.evaluate(VERSION_1, Optional.of(SOURCE_TIME.plusSeconds(1)), FINGERPRINT_1));

    assertMapping(mapping, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
  }

  @Test
  void shouldReturnCurrentMappingForOlderTimestamp() {
    InvoiceSourceMapping mapping = mapping(VERSION_2, Optional.of(SOURCE_TIME), FINGERPRINT_2);

    InvoiceSourceRecordDecision decision =
        mapping.evaluate(VERSION_1, Optional.of(SOURCE_TIME.minusSeconds(1)), FINGERPRINT_1);

    assertEquals(SourceRecordAcceptance.STALE, decision.acceptance());
    assertSame(mapping, decision.resultingMapping());
    assertMapping(mapping, VERSION_2, Optional.of(SOURCE_TIME), FINGERPRINT_2);
  }

  @Test
  void shouldAcceptNewerTimestampWithoutMutatingCurrentMapping() {
    InvoiceSourceMapping mapping = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    InvoiceSourceRecordDecision decision =
        mapping.evaluate(VERSION_2, Optional.of(SOURCE_TIME.plusSeconds(1)), FINGERPRINT_2);

    assertEquals(SourceRecordAcceptance.ACCEPTED, decision.acceptance());
    assertNotSame(mapping, decision.resultingMapping());
    assertMapping(mapping, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
    assertMapping(
        decision.resultingMapping(),
        VERSION_2,
        Optional.of(SOURCE_TIME.plusSeconds(1)),
        FINGERPRINT_2);
  }

  @Test
  void shouldRejectDifferentVersionsWithEqualTimestamps() {
    InvoiceSourceMapping mapping = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> mapping.evaluate(VERSION_2, Optional.of(SOURCE_TIME), FINGERPRINT_2));

    assertMapping(mapping, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
  }

  @Test
  void shouldRejectDifferentVersionsWithoutComparableTimestamps() {
    InvoiceSourceMapping withoutTimestamp = mapping(VERSION_1, Optional.empty(), FINGERPRINT_1);
    InvoiceSourceMapping withTimestamp =
        mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> withoutTimestamp.evaluate(VERSION_2, Optional.empty(), FINGERPRINT_2));
    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> withoutTimestamp.evaluate(VERSION_2, Optional.of(SOURCE_TIME), FINGERPRINT_2));
    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () -> withTimestamp.evaluate(VERSION_2, Optional.empty(), FINGERPRINT_2));

    assertMapping(withoutTimestamp, VERSION_1, Optional.empty(), FINGERPRINT_1);
    assertMapping(withTimestamp, VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);
  }

  @Test
  void shouldValidateIncomingEvidenceAndDecisionFields() {
    InvoiceSourceMapping mapping = mapping(VERSION_1, Optional.of(SOURCE_TIME), FINGERPRINT_1);

    assertThrows(
        NullPointerException.class,
        () -> mapping.evaluate(null, Optional.of(SOURCE_TIME), FINGERPRINT_1));
    assertThrows(
        NullPointerException.class, () -> mapping.evaluate(VERSION_1, null, FINGERPRINT_1));
    assertThrows(
        NullPointerException.class,
        () -> mapping.evaluate(VERSION_1, Optional.of(SOURCE_TIME), null));
    assertThrows(NullPointerException.class, () -> new InvoiceSourceRecordDecision(null, mapping));
    assertThrows(
        NullPointerException.class,
        () -> new InvoiceSourceRecordDecision(SourceRecordAcceptance.ACCEPTED, null));
  }

  private static InvoiceSourceMapping mapping(
      SourceRecordVersion version,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint fingerprint) {
    return InvoiceSourceMapping.create(
        TENANT_ID,
        SOURCE_SYSTEM_ID,
        SOURCE_IDENTITY,
        INVOICE_ID,
        version,
        sourceModifiedAt,
        fingerprint);
  }

  private static SourceRecordFingerprint fingerprint(char value) {
    return SourceRecordFingerprint.of(String.valueOf(value).repeat(64));
  }

  private static void assertMapping(
      InvoiceSourceMapping mapping,
      SourceRecordVersion version,
      Optional<Instant> sourceModifiedAt,
      SourceRecordFingerprint fingerprint) {
    assertEquals(TENANT_ID, mapping.tenantId());
    assertEquals(SOURCE_SYSTEM_ID, mapping.sourceSystemId());
    assertEquals(SOURCE_IDENTITY, mapping.sourceIdentity());
    assertEquals(INVOICE_ID, mapping.invoiceId());
    assertEquals(version, mapping.sourceVersion());
    assertEquals(sourceModifiedAt, mapping.sourceModifiedAt());
    assertEquals(fingerprint, mapping.payloadFingerprint());
  }
}
