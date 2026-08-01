package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorHealth;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "connectors")
class ConnectorJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false, length = ConnectorName.MAX_LENGTH)
  private String name;

  @Column(
      name = "connector_type",
      nullable = false,
      updatable = false,
      length = ConnectorType.MAX_LENGTH)
  private String connectorType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private ConnectorStatus status;

  @Column(name = "endpoint", nullable = false, length = ConnectorEndpoint.MAX_LENGTH)
  private String endpoint;

  @Column(name = "credential_reference", nullable = false)
  private UUID credentialReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "sync_mode", nullable = false, length = 32)
  private SyncPolicy.Mode syncMode;

  @Column(name = "sync_interval", nullable = false, length = 64)
  private String syncInterval;

  @Enumerated(EnumType.STRING)
  @Column(name = "health", nullable = false, length = 32)
  private ConnectorHealth health;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ConnectorJpaEntity() {}

  ConnectorJpaEntity(
      UUID id,
      UUID tenantId,
      String name,
      String connectorType,
      ConnectorStatus status,
      String endpoint,
      UUID credentialReference,
      SyncPolicy.Mode syncMode,
      String syncInterval,
      ConnectorHealth health,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "Connector id cannot be null");
    this.tenantId = Objects.requireNonNull(tenantId, "Connector tenant id cannot be null");
    this.name = Objects.requireNonNull(name, "Connector name cannot be null");
    this.connectorType = Objects.requireNonNull(connectorType, "Connector type cannot be null");
    this.status = Objects.requireNonNull(status, "Connector status cannot be null");
    this.endpoint = Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    this.credentialReference =
        Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
    this.syncMode = Objects.requireNonNull(syncMode, "Sync mode cannot be null");
    this.syncInterval = Objects.requireNonNull(syncInterval, "Sync interval cannot be null");
    this.health = Objects.requireNonNull(health, "Connector health cannot be null");
    this.createdAt = Objects.requireNonNull(createdAt, "Creation timestamp cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  void updateMutableState(
      String name,
      ConnectorStatus status,
      String endpoint,
      UUID credentialReference,
      SyncPolicy.Mode syncMode,
      String syncInterval,
      ConnectorHealth health,
      Instant updatedAt) {
    this.name = Objects.requireNonNull(name, "Connector name cannot be null");
    this.status = Objects.requireNonNull(status, "Connector status cannot be null");
    this.endpoint = Objects.requireNonNull(endpoint, "Connector endpoint cannot be null");
    this.credentialReference =
        Objects.requireNonNull(credentialReference, "Credential reference cannot be null");
    this.syncMode = Objects.requireNonNull(syncMode, "Sync mode cannot be null");
    this.syncInterval = Objects.requireNonNull(syncInterval, "Sync interval cannot be null");
    this.health = Objects.requireNonNull(health, "Connector health cannot be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "Update timestamp cannot be null");
  }

  UUID getId() {
    return id;
  }

  UUID getTenantId() {
    return tenantId;
  }

  String getName() {
    return name;
  }

  String getConnectorType() {
    return connectorType;
  }

  ConnectorStatus getStatus() {
    return status;
  }

  String getEndpoint() {
    return endpoint;
  }

  UUID getCredentialReference() {
    return credentialReference;
  }

  SyncPolicy.Mode getSyncMode() {
    return syncMode;
  }

  String getSyncInterval() {
    return syncInterval;
  }

  ConnectorHealth getHealth() {
    return health;
  }

  long getVersion() {
    return version;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }
}
