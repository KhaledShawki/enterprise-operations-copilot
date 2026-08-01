package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorHealth;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorPersistenceMapperTest {

  private static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");
  private static final ConnectorId CONNECTOR_ID =
      ConnectorId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final CredentialReference CREDENTIAL_REFERENCE =
      CredentialReference.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  private final ConnectorPersistenceMapper mapper = new ConnectorPersistenceMapper();

  @Test
  void shouldMapConnectorToJpaEntityWithoutCredentialMaterial() {
    Connector connector = scheduledConnector();

    ConnectorJpaEntity entity = mapper.toEntity(connector, NOW);

    assertAll(
        () -> assertEquals(CONNECTOR_ID.value(), entity.getId()),
        () -> assertEquals(TENANT_ID.value(), entity.getTenantId()),
        () -> assertEquals("Primary ERP", entity.getName()),
        () -> assertEquals("mock-erp", entity.getConnectorType()),
        () -> assertEquals(ConnectorStatus.ACTIVE, entity.getStatus()),
        () -> assertEquals("https://erp.example.com/api", entity.getEndpoint()),
        () -> assertEquals(CREDENTIAL_REFERENCE.value(), entity.getCredentialReference()),
        () -> assertEquals(SyncPolicy.Mode.SCHEDULED, entity.getSyncMode()),
        () -> assertEquals("PT15M0.123456789S", entity.getSyncInterval()),
        () -> assertEquals(ConnectorHealth.HEALTHY, entity.getHealth()),
        () -> assertEquals(NOW, entity.getCreatedAt()),
        () -> assertEquals(NOW, entity.getUpdatedAt()));
  }

  @Test
  void shouldReconstituteConnectorWithoutLosingSyncIntervalPrecision() {
    ConnectorJpaEntity entity = mapper.toEntity(scheduledConnector(), NOW);

    Connector connector = mapper.toDomain(entity);

    assertAll(
        () -> assertEquals(CONNECTOR_ID, connector.id()),
        () -> assertEquals(TENANT_ID, connector.tenantId()),
        () -> assertEquals(ConnectorName.of("Primary ERP"), connector.name()),
        () -> assertEquals(ConnectorType.of("mock-erp"), connector.type()),
        () -> assertEquals(ConnectorStatus.ACTIVE, connector.status()),
        () ->
            assertEquals(ConnectorEndpoint.of("https://erp.example.com/api"), connector.endpoint()),
        () -> assertEquals(CREDENTIAL_REFERENCE, connector.credentialReference()),
        () ->
            assertEquals(
                SyncPolicy.scheduled(Duration.ofSeconds(900, 123_456_789)), connector.syncPolicy()),
        () -> assertEquals(ConnectorHealth.HEALTHY, connector.health()));
  }

  @Test
  void shouldUpdateOnlyMutableJpaState() {
    Connector original = scheduledConnector();
    ConnectorJpaEntity entity = mapper.toEntity(original, NOW);
    Instant updatedAt = Instant.parse("2026-08-01T09:00:00Z");

    Connector updated =
        Connector.reconstitute(
            CONNECTOR_ID,
            TENANT_ID,
            ConnectorName.of("Renamed ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorStatus.SUSPENDED,
            ConnectorEndpoint.of("https://erp.example.com/api/v2"),
            CredentialReference.of(UUID.fromString("00000000-0000-0000-0000-000000000004")),
            SyncPolicy.manual(),
            ConnectorHealth.UNHEALTHY);

    ConnectorJpaEntity updatedEntity = mapper.updateEntity(updated, entity, updatedAt);

    assertSame(entity, updatedEntity);
    assertAll(
        () -> assertEquals(CONNECTOR_ID.value(), updatedEntity.getId()),
        () -> assertEquals(TENANT_ID.value(), updatedEntity.getTenantId()),
        () -> assertEquals("mock-erp", updatedEntity.getConnectorType()),
        () -> assertEquals("Renamed ERP", updatedEntity.getName()),
        () -> assertEquals(ConnectorStatus.SUSPENDED, updatedEntity.getStatus()),
        () -> assertEquals("https://erp.example.com/api/v2", updatedEntity.getEndpoint()),
        () -> assertEquals(SyncPolicy.Mode.MANUAL, updatedEntity.getSyncMode()),
        () -> assertEquals("PT0S", updatedEntity.getSyncInterval()),
        () -> assertEquals(ConnectorHealth.UNHEALTHY, updatedEntity.getHealth()),
        () -> assertEquals(NOW, updatedEntity.getCreatedAt()),
        () -> assertEquals(updatedAt, updatedEntity.getUpdatedAt()));
  }

  @Test
  void shouldRejectImmutableIdentityMismatches() {
    Connector connector = scheduledConnector();
    ConnectorJpaEntity entity = mapper.toEntity(connector, NOW);

    assertThrows(
        IllegalArgumentException.class,
        () -> mapper.updateEntity(withId(ConnectorId.generate()), entity, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            mapper.updateEntity(
                withTenantId(ConnectorTenantId.of(UUID.randomUUID())), entity, NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> mapper.updateEntity(withType(ConnectorType.of("sap-b1")), entity, NOW));
  }

  @Test
  void shouldRejectNullMappingInputs() {
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null, NOW));
    assertThrows(NullPointerException.class, () -> mapper.toEntity(scheduledConnector(), null));
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
    assertThrows(
        NullPointerException.class,
        () -> mapper.updateEntity(null, mapper.toEntity(scheduledConnector(), NOW), NOW));
    assertThrows(
        NullPointerException.class, () -> mapper.updateEntity(scheduledConnector(), null, NOW));
    assertThrows(
        NullPointerException.class,
        () ->
            mapper.updateEntity(
                scheduledConnector(), mapper.toEntity(scheduledConnector(), NOW), null));
  }

  private static Connector scheduledConnector() {
    return Connector.reconstitute(
        CONNECTOR_ID,
        TENANT_ID,
        ConnectorName.of("Primary ERP"),
        ConnectorType.of("mock-erp"),
        ConnectorStatus.ACTIVE,
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CREDENTIAL_REFERENCE,
        SyncPolicy.scheduled(Duration.ofSeconds(900, 123_456_789)),
        ConnectorHealth.HEALTHY);
  }

  private static Connector withId(ConnectorId connectorId) {
    Connector connector = scheduledConnector();
    return Connector.reconstitute(
        connectorId,
        connector.tenantId(),
        connector.name(),
        connector.type(),
        connector.status(),
        connector.endpoint(),
        connector.credentialReference(),
        connector.syncPolicy(),
        connector.health());
  }

  private static Connector withTenantId(ConnectorTenantId tenantId) {
    Connector connector = scheduledConnector();
    return Connector.reconstitute(
        connector.id(),
        tenantId,
        connector.name(),
        connector.type(),
        connector.status(),
        connector.endpoint(),
        connector.credentialReference(),
        connector.syncPolicy(),
        connector.health());
  }

  private static Connector withType(ConnectorType connectorType) {
    Connector connector = scheduledConnector();
    return Connector.reconstitute(
        connector.id(),
        connector.tenantId(),
        connector.name(),
        connectorType,
        connector.status(),
        connector.endpoint(),
        connector.credentialReference(),
        connector.syncPolicy(),
        connector.health());
  }
}
