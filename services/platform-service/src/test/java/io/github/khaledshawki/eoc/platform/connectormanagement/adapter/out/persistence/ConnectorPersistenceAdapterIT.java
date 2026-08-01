package io.github.khaledshawki.eoc.platform.connectormanagement.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.connectormanagement.application.exception.ConnectorNameAlreadyExistsException;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorHealth;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({
  TestcontainersConfiguration.class,
  ConnectorPersistenceAdapterIT.FixedClockConfiguration.class
})
class ConnectorPersistenceAdapterIT {

  private static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
  private static final ConnectorTenantId OTHER_TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));

  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private SpringDataConnectorRepository springDataConnectorRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    springDataConnectorRepository.deleteAllInBatch();
  }

  @Test
  void shouldSaveAndFindConnectorWithinItsTenant() {
    Connector connector = connector(TENANT_ID, "Primary ERP");

    Connector saved = connectorRepository.save(connector);

    assertEquals(connector.id(), saved.id());
    assertEquals(TENANT_ID, saved.tenantId());
    assertEquals(ConnectorStatus.DRAFT, saved.status());
    assertTrue(
        connectorRepository.existsByTenantIdAndName(TENANT_ID, ConnectorName.of("Primary ERP")));
    assertFalse(
        connectorRepository.existsByTenantIdAndName(
            OTHER_TENANT_ID, ConnectorName.of("Primary ERP")));

    Connector loaded = connectorRepository.findById(TENANT_ID, connector.id()).orElseThrow();

    assertEquals(saved.id(), loaded.id());
    assertEquals(saved.tenantId(), loaded.tenantId());
    assertEquals(saved.name(), loaded.name());
    assertEquals(saved.type(), loaded.type());
    assertEquals(saved.status(), loaded.status());
    assertEquals(saved.endpoint(), loaded.endpoint());
    assertEquals(saved.credentialReference(), loaded.credentialReference());
    assertEquals(saved.syncPolicy(), loaded.syncPolicy());
    assertEquals(saved.health(), loaded.health());

    assertTrue(connectorRepository.findById(OTHER_TENANT_ID, connector.id()).isEmpty());

    ConnectorJpaEntity stored =
        springDataConnectorRepository.findById(connector.id().value()).orElseThrow();
    assertEquals(0L, stored.getVersion());
    assertEquals(NOW, stored.getCreatedAt());
    assertEquals(NOW, stored.getUpdatedAt());
  }

  @Test
  void shouldListTenantConnectorsInStableNameOrder() {
    Connector zebra = connectorRepository.save(connector(TENANT_ID, "Zebra ERP"));
    Connector alpha = connectorRepository.save(connector(TENANT_ID, "Alpha ERP"));
    connectorRepository.save(connector(OTHER_TENANT_ID, "Other ERP"));

    List<Connector> connectors = connectorRepository.findAllByTenantId(TENANT_ID);

    assertEquals(List.of(alpha.id(), zebra.id()), connectors.stream().map(Connector::id).toList());
    assertTrue(
        connectorRepository.findAllByTenantId(ConnectorTenantId.of(UUID.randomUUID())).isEmpty());
  }

  @Test
  void shouldUpdateMutableConnectorStateAndIncrementVersion() {
    Connector connector = connectorRepository.save(connector(TENANT_ID, "Primary ERP"));

    connector.rename(ConnectorName.of("Renamed ERP"));
    connector.activate();
    connector.updateEndpoint(ConnectorEndpoint.of("https://erp.example.com/api/v2"));
    connector.updateCredentialReference(CredentialReference.of(UUID.randomUUID()));
    connector.updateSyncPolicy(SyncPolicy.scheduled(Duration.ofMinutes(30)));
    connector.updateHealth(ConnectorHealth.HEALTHY);

    Connector updated = connectorRepository.save(connector);

    assertEquals(ConnectorName.of("Renamed ERP"), updated.name());
    assertEquals(ConnectorStatus.ACTIVE, updated.status());
    assertEquals(ConnectorEndpoint.of("https://erp.example.com/api/v2"), updated.endpoint());
    assertEquals(SyncPolicy.scheduled(Duration.ofMinutes(30)), updated.syncPolicy());
    assertEquals(ConnectorHealth.HEALTHY, updated.health());

    ConnectorJpaEntity stored =
        springDataConnectorRepository.findById(connector.id().value()).orElseThrow();
    assertEquals(TENANT_ID.value(), stored.getTenantId());
    assertEquals("mock-erp", stored.getConnectorType());
    assertEquals(1L, stored.getVersion());
    assertEquals(NOW, stored.getCreatedAt());
    assertEquals(NOW, stored.getUpdatedAt());
  }

  @Test
  void shouldEnforceTenantScopedConnectorNameUniqueness() {
    connectorRepository.save(connector(TENANT_ID, "Primary ERP"));

    ConnectorNameAlreadyExistsException exception =
        assertThrows(
            ConnectorNameAlreadyExistsException.class,
            () -> connectorRepository.save(connector(TENANT_ID, "Primary ERP")));

    assertInstanceOf(DataIntegrityViolationException.class, exception.getCause());
    assertEquals(1L, springDataConnectorRepository.count());

    connectorRepository.save(connector(OTHER_TENANT_ID, "Primary ERP"));

    assertEquals(2L, springDataConnectorRepository.count());
  }

  @Test
  void shouldPersistOnlyOpaqueCredentialReference() {
    Connector connector = connectorRepository.save(connector(TENANT_ID, "Primary ERP"));

    UUID storedCredentialReference =
        jdbcTemplate.queryForObject(
            "SELECT credential_reference FROM connectors WHERE id = ?",
            UUID.class,
            connector.id().value());

    assertEquals(connector.credentialReference().value(), storedCredentialReference);

    List<String> columns =
        jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'connectors'
            """,
            String.class);

    assertTrue(columns.contains("credential_reference"));
    assertTrue(
        columns.stream()
            .noneMatch(
                column ->
                    column.contains("password")
                        || column.contains("secret")
                        || column.contains("token")
                        || column.equals("credential_value")));
  }

  @Test
  void shouldRejectConcurrentUpdatesWithAStaleVersion() {
    Connector connector = connectorRepository.save(connector(TENANT_ID, "Primary ERP"));

    EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
    EntityManager secondEntityManager = entityManagerFactory.createEntityManager();
    EntityTransaction firstTransaction = firstEntityManager.getTransaction();
    EntityTransaction secondTransaction = secondEntityManager.getTransaction();

    try {
      firstTransaction.begin();
      secondTransaction.begin();

      ConnectorJpaEntity firstCopy =
          firstEntityManager.find(ConnectorJpaEntity.class, connector.id().value());
      ConnectorJpaEntity secondCopy =
          secondEntityManager.find(ConnectorJpaEntity.class, connector.id().value());

      updateName(firstCopy, "First update");
      updateName(secondCopy, "Stale update");

      firstTransaction.commit();

      RuntimeException exception = assertThrows(RuntimeException.class, secondTransaction::commit);
      assertTrue(hasCause(exception, OptimisticLockException.class));
    } finally {
      if (firstTransaction.isActive()) {
        firstTransaction.rollback();
      }
      if (secondTransaction.isActive()) {
        secondTransaction.rollback();
      }
      firstEntityManager.close();
      secondEntityManager.close();
    }
  }

  private static Connector connector(ConnectorTenantId tenantId, String name) {
    return Connector.create(
        tenantId,
        ConnectorName.of(name),
        ConnectorType.of("mock-erp"),
        ConnectorEndpoint.of("https://erp.example.com/api"),
        CredentialReference.of(UUID.randomUUID()),
        SyncPolicy.manual());
  }

  private static void updateName(ConnectorJpaEntity entity, String name) {
    entity.updateMutableState(
        name,
        entity.getStatus(),
        entity.getEndpoint(),
        entity.getCredentialReference(),
        entity.getSyncMode(),
        entity.getSyncInterval(),
        entity.getHealth(),
        NOW);
  }

  private static boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
    Throwable current = exception;
    while (current != null) {
      if (causeType.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock connectorPersistenceClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
