package io.github.khaledshawki.eoc.platform.connectormanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.model.outbox.PublishConnectorOutboxBatchResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunLifecycleUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunReference;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.ImportRunResult;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.PublishConnectorOutboxBatchUseCase;
import io.github.khaledshawki.eoc.connectormanagement.application.port.in.RequestImportRunCommand;
import io.github.khaledshawki.eoc.connectormanagement.application.port.out.ConnectorRepository;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.Connector;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorEndpoint;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorName;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorTenantId;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ConnectorType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.CredentialReference;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportMode;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportStatus;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.ImportType;
import io.github.khaledshawki.eoc.connectormanagement.domain.model.SyncPolicy;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, ConnectorOutboxRuntimeIT.FixedClockConfiguration.class})
class ConnectorOutboxRuntimeIT {

  private static final Instant NOW = Instant.parse("2026-08-03T19:00:00Z");
  private static final ConnectorTenantId TENANT_ID =
      ConnectorTenantId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));

  @Autowired private ConnectorRepository connectorRepository;
  @Autowired private ImportRunLifecycleUseCase importRunLifecycleUseCase;
  @Autowired private PublishConnectorOutboxBatchUseCase publishConnectorOutboxBatchUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM connector_import_run_event_projection");
    jdbcTemplate.update("DELETE FROM connector_inbox_events");
    jdbcTemplate.update("DELETE FROM connector_outbox_events");
    jdbcTemplate.update("DELETE FROM connector_import_page_acceptances");
    jdbcTemplate.update("DELETE FROM connector_import_checkpoints");
    jdbcTemplate.update("DELETE FROM connector_import_runs");
    jdbcTemplate.update("DELETE FROM connectors");
  }

  @Test
  void shouldPublishACompletedImportEventThroughTheWiredLocalRuntime() {
    Connector connector = connectorRepository.save(activeConnector());
    ImportRunResult requested =
        importRunLifecycleUseCase.request(
            new RequestImportRunCommand(
                TENANT_ID.value(),
                connector.id().value(),
                ImportType.CUSTOMERS,
                ImportMode.INCREMENTAL));
    ImportRunReference reference =
        new ImportRunReference(TENANT_ID.value(), requested.importRunId().value());
    importRunLifecycleUseCase.start(reference);
    ImportRunResult completed = importRunLifecycleUseCase.complete(reference);

    assertEquals(ImportStatus.COMPLETED, completed.status());
    assertEquals(1, countByStatus("connector_outbox_events", "PENDING"));

    PublishConnectorOutboxBatchResult first =
        publishConnectorOutboxBatchUseCase.publishBatch(
            new PublishConnectorOutboxBatchCommand(
                "runtime-integration-worker", 10, Duration.ofMinutes(1)));
    PublishConnectorOutboxBatchResult replay =
        publishConnectorOutboxBatchUseCase.publishBatch(
            new PublishConnectorOutboxBatchCommand(
                "runtime-integration-worker", 10, Duration.ofMinutes(1)));

    assertEquals(new PublishConnectorOutboxBatchResult(1, 1, 0, 0), first);
    assertEquals(PublishConnectorOutboxBatchResult.empty(), replay);
    assertEquals(1, countByStatus("connector_outbox_events", "PUBLISHED"));
    assertEquals(1, count("connector_inbox_events"));
    assertEquals(1, count("connector_import_run_event_projection"));
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private int countByStatus(String table, String status) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE publish_status = ?", Integer.class, status);
  }

  private static Connector activeConnector() {
    Connector connector =
        Connector.create(
            TENANT_ID,
            ConnectorName.of("Runtime ERP"),
            ConnectorType.of("mock-erp"),
            ConnectorEndpoint.of("https://runtime.example.com/api"),
            CredentialReference.of(UUID.randomUUID()),
            SyncPolicy.manual());
    connector.activate();
    return connector;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock connectorOutboxRuntimeClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
