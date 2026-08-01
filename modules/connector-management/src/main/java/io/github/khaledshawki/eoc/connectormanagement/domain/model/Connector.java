package io.github.khaledshawki.eoc.connectormanagement.domain.model;

import java.util.Objects;

public final class Connector {

  private final ConnectorId id;
  private final ConnectorTenantId tenantId;
  private ConnectorName name;
  private final ConnectorType type;
  private ConnectorStatus status;
  private ConnectorEndpoint endpoint;
  private CredentialReference credentialReference;
  private SyncPolicy syncPolicy;
  private ConnectorHealth health;

  private Connector(
      ConnectorId id,
      ConnectorTenantId tenantId,
      ConnectorName name,
      ConnectorType type,
      ConnectorStatus status,
      ConnectorEndpoint endpoint,
      CredentialReference credentialReference,
      SyncPolicy syncPolicy,
      ConnectorHealth health) {
    this.id = Objects.requireNonNull(id, "Connector id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    this.name = Objects.requireNonNull(name, "Connector name cannot be null");
    this.type = Objects.requireNonNull(type, "Connector type cannot be null");
    this.status = Objects.requireNonNull(status, "Connector status cannot be null");
    this.endpoint = Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    this.credentialReference =
        Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
    this.syncPolicy = Objects.requireNonNull(syncPolicy, "Sync policy cannot be null");
    this.health = Objects.requireNonNull(health, "Connector health cannot be null");
  }

  public static Connector create(
      ConnectorTenantId tenantId,
      ConnectorName name,
      ConnectorType type,
      ConnectorEndpoint endpoint,
      CredentialReference credentialReference,
      SyncPolicy syncPolicy) {
    return new Connector(
        ConnectorId.generate(),
        tenantId,
        name,
        type,
        ConnectorStatus.DRAFT,
        endpoint,
        credentialReference,
        syncPolicy,
        ConnectorHealth.UNKNOWN);
  }

  public static Connector reconstitute(
      ConnectorId id,
      ConnectorTenantId tenantId,
      ConnectorName name,
      ConnectorType type,
      ConnectorStatus status,
      ConnectorEndpoint endpoint,
      CredentialReference credentialReference,
      SyncPolicy syncPolicy,
      ConnectorHealth health) {
    return new Connector(
        id, tenantId, name, type, status, endpoint, credentialReference, syncPolicy, health);
  }

  public void rename(ConnectorName name) {
    this.name = Objects.requireNonNull(name, "Connector name cannot be null");
  }

  public void updateEndpoint(ConnectorEndpoint endpoint) {
    this.endpoint = Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
  }

  public void updateCredentialReference(CredentialReference credentialReference) {
    this.credentialReference =
        Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
  }

  public void updateSyncPolicy(SyncPolicy syncPolicy) {
    this.syncPolicy = Objects.requireNonNull(syncPolicy, "Sync policy cannot be null");
  }

  public void updateHealth(ConnectorHealth health) {
    this.health = Objects.requireNonNull(health, "Connector health cannot be null");
  }

  public void activate() {
    if (status == ConnectorStatus.ACTIVE) {
      throw new IllegalStateException("Connector is already active");
    }

    status = ConnectorStatus.ACTIVE;
  }

  public void suspend() {
    if (status == ConnectorStatus.DRAFT) {
      throw new IllegalStateException("Draft connector cannot be suspended");
    }

    if (status == ConnectorStatus.SUSPENDED) {
      throw new IllegalStateException("Connector is already suspended");
    }

    status = ConnectorStatus.SUSPENDED;
  }

  public void ensureCanStartSync() {
    if (status != ConnectorStatus.ACTIVE) {
      throw new IllegalStateException("Only active connectors can start sync work");
    }
  }

  public ConnectorId id() {
    return id;
  }

  public ConnectorTenantId tenantId() {
    return tenantId;
  }

  public ConnectorName name() {
    return name;
  }

  public ConnectorType type() {
    return type;
  }

  public ConnectorStatus status() {
    return status;
  }

  public ConnectorEndpoint endpoint() {
    return endpoint;
  }

  public CredentialReference credentialReference() {
    return credentialReference;
  }

  public SyncPolicy syncPolicy() {
    return syncPolicy;
  }

  public ConnectorHealth health() {
    return health;
  }
}
