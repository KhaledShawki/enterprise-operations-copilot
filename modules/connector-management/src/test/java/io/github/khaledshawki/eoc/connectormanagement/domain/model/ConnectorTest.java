package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectorTest {

  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final ConnectorName NAME = ConnectorName.of("Primary ERP");
  private static final ConnectorType TYPE = ConnectorType.of("mock-erp");
  private static final ConnectorEndpoint ENDPOINT =
      ConnectorEndpoint.of("https://erp.example.com/api");
  private static final CredentialReference CREDENTIAL_REFERENCE =
      CredentialReference.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SyncPolicy SYNC_POLICY = SyncPolicy.manual();

  @Test
  void shouldCreateDraftConnectorWithGeneratedIdAndUnknownHealth() {
    Connector connector = createConnector();

    assertAll(
        () -> assertNotNull(connector.id()),
        () -> assertEquals(TENANT_ID, connector.tenantId()),
        () -> assertEquals(NAME, connector.name()),
        () -> assertEquals(TYPE, connector.type()),
        () -> assertEquals(ConnectorStatus.DRAFT, connector.status()),
        () -> assertEquals(ENDPOINT, connector.endpoint()),
        () -> assertEquals(CREDENTIAL_REFERENCE, connector.credentialReference()),
        () -> assertEquals(SYNC_POLICY, connector.syncPolicy()),
        () -> assertEquals(ConnectorHealth.UNKNOWN, connector.health()));
  }

  @Test
  void shouldReconstituteExistingConnector() {
    ConnectorId connectorId = ConnectorId.generate();
    Connector connector =
        Connector.reconstitute(
            connectorId,
            TENANT_ID,
            NAME,
            TYPE,
            ConnectorStatus.SUSPENDED,
            ENDPOINT,
            CREDENTIAL_REFERENCE,
            SYNC_POLICY,
            ConnectorHealth.UNHEALTHY);

    assertAll(
        () -> assertEquals(connectorId, connector.id()),
        () -> assertEquals(TENANT_ID, connector.tenantId()),
        () -> assertEquals(ConnectorStatus.SUSPENDED, connector.status()),
        () -> assertEquals(ConnectorHealth.UNHEALTHY, connector.health()));
  }

  @Test
  void shouldUpdateMutableConfigurationWithoutChangingTenantOwnershipOrType() {
    Connector connector = createConnector();
    ConnectorName newName = ConnectorName.of("Renamed ERP");
    ConnectorEndpoint newEndpoint = ConnectorEndpoint.of("https://erp.example.com/api/v2");
    CredentialReference newCredentialReference = CredentialReference.of(UUID.randomUUID());
    SyncPolicy newSyncPolicy = SyncPolicy.scheduled(java.time.Duration.ofMinutes(30));

    connector.rename(newName);
    connector.updateEndpoint(newEndpoint);
    connector.updateCredentialReference(newCredentialReference);
    connector.updateSyncPolicy(newSyncPolicy);
    connector.updateHealth(ConnectorHealth.HEALTHY);

    assertAll(
        () -> assertEquals(TENANT_ID, connector.tenantId()),
        () -> assertEquals(TYPE, connector.type()),
        () -> assertEquals(newName, connector.name()),
        () -> assertEquals(newEndpoint, connector.endpoint()),
        () -> assertEquals(newCredentialReference, connector.credentialReference()),
        () -> assertEquals(newSyncPolicy, connector.syncPolicy()),
        () -> assertEquals(ConnectorHealth.HEALTHY, connector.health()));
  }

  @Test
  void shouldActivateDraftAndSuspendedConnector() {
    Connector connector = createConnector();

    connector.activate();
    assertEquals(ConnectorStatus.ACTIVE, connector.status());

    connector.suspend();
    connector.activate();
    assertEquals(ConnectorStatus.ACTIVE, connector.status());
  }

  @Test
  void shouldSuspendActiveConnector() {
    Connector connector = createConnector();
    connector.activate();

    connector.suspend();

    assertEquals(ConnectorStatus.SUSPENDED, connector.status());
  }

  @Test
  void shouldRejectInvalidLifecycleTransitions() {
    Connector connector = createConnector();

    IllegalStateException draftSuspension =
        assertThrows(IllegalStateException.class, connector::suspend);
    assertEquals("Draft connector cannot be suspended", draftSuspension.getMessage());

    connector.activate();
    IllegalStateException duplicateActivation =
        assertThrows(IllegalStateException.class, connector::activate);
    assertEquals("Connector is already active", duplicateActivation.getMessage());

    connector.suspend();
    IllegalStateException duplicateSuspension =
        assertThrows(IllegalStateException.class, connector::suspend);
    assertEquals("Connector is already suspended", duplicateSuspension.getMessage());
  }

  @Test
  void shouldAllowSyncWorkOnlyWhileActive() {
    Connector connector = createConnector();

    IllegalStateException draftFailure =
        assertThrows(IllegalStateException.class, connector::ensureCanStartSync);
    assertEquals("Only active connectors can start sync work", draftFailure.getMessage());

    connector.activate();
    connector.ensureCanStartSync();

    connector.suspend();
    IllegalStateException suspendedFailure =
        assertThrows(IllegalStateException.class, connector::ensureCanStartSync);
    assertEquals("Only active connectors can start sync work", suspendedFailure.getMessage());
  }

  @Test
  void shouldRejectNullCreationAndReconstitutionValues() {
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(null, NAME, TYPE, ENDPOINT, CREDENTIAL_REFERENCE, SYNC_POLICY));
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(TENANT_ID, null, TYPE, ENDPOINT, CREDENTIAL_REFERENCE, SYNC_POLICY));
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(TENANT_ID, NAME, null, ENDPOINT, CREDENTIAL_REFERENCE, SYNC_POLICY));
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(TENANT_ID, NAME, TYPE, null, CREDENTIAL_REFERENCE, SYNC_POLICY));
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(TENANT_ID, NAME, TYPE, ENDPOINT, null, SYNC_POLICY));
    assertThrows(
        NullPointerException.class,
        () -> Connector.create(TENANT_ID, NAME, TYPE, ENDPOINT, CREDENTIAL_REFERENCE, null));

    assertThrows(
        NullPointerException.class,
        () ->
            Connector.reconstitute(
                null,
                TENANT_ID,
                NAME,
                TYPE,
                ConnectorStatus.DRAFT,
                ENDPOINT,
                CREDENTIAL_REFERENCE,
                SYNC_POLICY,
                ConnectorHealth.UNKNOWN));
    assertThrows(
        NullPointerException.class,
        () ->
            Connector.reconstitute(
                ConnectorId.generate(),
                TENANT_ID,
                NAME,
                TYPE,
                null,
                ENDPOINT,
                CREDENTIAL_REFERENCE,
                SYNC_POLICY,
                ConnectorHealth.UNKNOWN));
    assertThrows(
        NullPointerException.class,
        () ->
            Connector.reconstitute(
                ConnectorId.generate(),
                TENANT_ID,
                NAME,
                TYPE,
                ConnectorStatus.DRAFT,
                ENDPOINT,
                CREDENTIAL_REFERENCE,
                SYNC_POLICY,
                null));
  }

  @Test
  void shouldRejectNullUpdates() {
    Connector connector = createConnector();

    assertThrows(NullPointerException.class, () -> connector.rename(null));
    assertThrows(NullPointerException.class, () -> connector.updateEndpoint(null));
    assertThrows(NullPointerException.class, () -> connector.updateCredentialReference(null));
    assertThrows(NullPointerException.class, () -> connector.updateSyncPolicy(null));
    assertThrows(NullPointerException.class, () -> connector.updateHealth(null));
  }

  private static Connector createConnector() {
    return Connector.create(TENANT_ID, NAME, TYPE, ENDPOINT, CREDENTIAL_REFERENCE, SYNC_POLICY);
  }
}
