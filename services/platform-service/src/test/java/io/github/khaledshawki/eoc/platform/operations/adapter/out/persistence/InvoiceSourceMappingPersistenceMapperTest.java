package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.operations.domain.model.InvoiceId;
import io.github.khaledshawki.eoc.operations.domain.model.InvoiceSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordFingerprint;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceSourceMappingPersistenceMapperTest {

  private static final OperationsTenantId TENANT_ID =
      OperationsTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final SourceSystemId SOURCE_SYSTEM_ID =
      SourceSystemId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SourceRecordIdentity SOURCE_IDENTITY =
      SourceRecordIdentity.sourceRecordId("invoice-1");
  private static final InvoiceId INVOICE_ID =
      InvoiceId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");
  private static final String FIRST_FINGERPRINT = "a".repeat(64);
  private static final String SECOND_FINGERPRINT = "b".repeat(64);

  private final InvoiceSourceMappingPersistenceMapper mapper =
      new InvoiceSourceMappingPersistenceMapper();

  @Test
  void shouldRoundTripSourceEvidence() {
    InvoiceSourceMapping mapping =
        InvoiceSourceMapping.create(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SOURCE_IDENTITY,
            INVOICE_ID,
            new SourceRecordVersion("v1"),
            Optional.of(NOW.minusSeconds(60)),
            SourceRecordFingerprint.of(FIRST_FINGERPRINT));

    InvoiceSourceMappingJpaEntity entity = mapper.toEntity(mapping, NOW);
    InvoiceSourceMapping restored = mapper.toDomain(entity);

    assertEquals(mapping.tenantId(), restored.tenantId());
    assertEquals(mapping.sourceSystemId(), restored.sourceSystemId());
    assertEquals(mapping.sourceIdentity(), restored.sourceIdentity());
    assertEquals(mapping.invoiceId(), restored.invoiceId());
    assertEquals(mapping.sourceVersion(), restored.sourceVersion());
    assertEquals(mapping.sourceModifiedAt(), restored.sourceModifiedAt());
    assertEquals(mapping.payloadFingerprint(), restored.payloadFingerprint());
  }

  @Test
  void shouldUpdateEvidenceAndRejectImmutableIdentityChanges() {
    InvoiceSourceMapping original =
        InvoiceSourceMapping.create(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SOURCE_IDENTITY,
            INVOICE_ID,
            new SourceRecordVersion("v1"),
            Optional.of(NOW.minusSeconds(60)),
            SourceRecordFingerprint.of(FIRST_FINGERPRINT));
    InvoiceSourceMappingJpaEntity entity = mapper.toEntity(original, NOW);
    Instant later = NOW.plusSeconds(60);
    InvoiceSourceMapping updated =
        InvoiceSourceMapping.reconstitute(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SOURCE_IDENTITY,
            INVOICE_ID,
            new SourceRecordVersion("v2"),
            Optional.of(NOW),
            SourceRecordFingerprint.of(SECOND_FINGERPRINT));

    mapper.updateEntity(updated, entity, later);

    assertEquals("v2", entity.getSourceVersion());
    assertEquals(NOW, entity.getSourceModifiedAt());
    assertEquals(SECOND_FINGERPRINT, entity.getPayloadFingerprint());
    assertEquals(later, entity.getUpdatedAt());

    InvoiceSourceMapping wrongInvoice =
        InvoiceSourceMapping.reconstitute(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            SOURCE_IDENTITY,
            InvoiceId.generate(),
            updated.sourceVersion(),
            updated.sourceModifiedAt(),
            updated.payloadFingerprint());
    assertThrows(
        IllegalArgumentException.class, () -> mapper.updateEntity(wrongInvoice, entity, later));
  }
}
