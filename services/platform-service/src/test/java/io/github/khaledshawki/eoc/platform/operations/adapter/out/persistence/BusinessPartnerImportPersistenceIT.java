package io.github.khaledshawki.eoc.platform.operations.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.operations.application.exception.ImportPageAcceptanceConflictException;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportRecord;
import io.github.khaledshawki.eoc.operations.application.port.in.BusinessPartnerImportResult;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersCommand;
import io.github.khaledshawki.eoc.operations.application.port.in.ImportBusinessPartnersUseCase;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerRepository;
import io.github.khaledshawki.eoc.operations.application.port.out.BusinessPartnerSourceMappingRepository;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartner;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerProfile;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerRole;
import io.github.khaledshawki.eoc.operations.domain.model.BusinessPartnerSourceMapping;
import io.github.khaledshawki.eoc.operations.domain.model.ConflictingSourceRecordVersionException;
import io.github.khaledshawki.eoc.operations.domain.model.OperationsTenantId;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordIdentity;
import io.github.khaledshawki.eoc.operations.domain.model.SourceRecordVersion;
import io.github.khaledshawki.eoc.operations.domain.model.SourceSystemId;
import io.github.khaledshawki.eoc.platform.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
@Import({
  TestcontainersConfiguration.class,
  BusinessPartnerImportPersistenceIT.MutableClockConfiguration.class
})
class BusinessPartnerImportPersistenceIT {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID OTHER_SOURCE_SYSTEM_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID IMPORT_BATCH_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000020");
  private static final UUID PAGE_ACCEPTANCE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000030");
  private static final Instant FIRST_IMPORT_TIME = Instant.parse("2026-08-02T08:00:00Z");
  private static final Instant SECOND_IMPORT_TIME = Instant.parse("2026-08-02T09:00:00Z");
  private static final Instant FIRST_SOURCE_TIME = Instant.parse("2026-08-01T08:00:00Z");
  private static final Instant SECOND_SOURCE_TIME = Instant.parse("2026-08-01T09:00:00Z");

  @Autowired private ImportBusinessPartnersUseCase importBusinessPartnersUseCase;
  @Autowired private BusinessPartnerRepository businessPartnerRepository;
  @Autowired private BusinessPartnerSourceMappingRepository sourceMappingRepository;
  @Autowired private SpringDataBusinessPartnerRepository springDataBusinessPartnerRepository;

  @Autowired
  private SpringDataBusinessPartnerSourceMappingRepository springDataSourceMappingRepository;

  @Autowired private SpringDataBusinessPartnerImportReceiptRepository springDataReceiptRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MutableClock clock;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "DROP TRIGGER IF EXISTS reject_operations_outbox_insert ON operations_outbox_events");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_operations_outbox_insert()");
    jdbcTemplate.update("DELETE FROM operations_outbox_events");
    jdbcTemplate.update("DELETE FROM operations_event_stream_versions");
    springDataReceiptRepository.deleteAllInBatch();
    springDataSourceMappingRepository.deleteAllInBatch();
    springDataBusinessPartnerRepository.deleteAllInBatch();
    clock.setInstant(FIRST_IMPORT_TIME);
  }

  @Test
  void shouldPersistAcceptedCustomersSourceMappingsRolesAndReceiptAtomically() {
    BusinessPartnerImportResult result =
        importBusinessPartnersUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(
                    record("customer-1", "v1", FIRST_SOURCE_TIME, "C-1", "Acme GmbH"),
                    record("customer-2", "v1", FIRST_SOURCE_TIME, "C-2", "Beta AG"))));

    assertEquals(2, result.fetchedCount());
    assertEquals(2, result.createdCount());
    assertEquals(2, result.acceptedCount());
    assertEquals(FIRST_IMPORT_TIME, result.acceptedAt());
    assertEquals(2, springDataBusinessPartnerRepository.count());
    assertEquals(2, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
    assertEquals(
        2L,
        jdbcTemplate.queryForObject("SELECT count(*) FROM operations_outbox_events", Long.class));
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM operations_outbox_events
            WHERE event_type = 'operations.business-partner.synchronized.v1'
              AND aggregate_version = 1
              AND publish_status = 'PENDING'
            """,
            Long.class));
    assertEquals(
        2L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_business_partner_roles WHERE role = 'CUSTOMER'",
            Long.class));

    BusinessPartnerSourceMapping mapping = mapping(TENANT_ID, SOURCE_SYSTEM_ID, "customer-1");
    BusinessPartner partner =
        businessPartnerRepository
            .findById(OperationsTenantId.of(TENANT_ID), mapping.businessPartnerId())
            .orElseThrow();
    assertEquals("Acme GmbH", partner.profile().displayName());
    assertEquals(java.util.Set.of(BusinessPartnerRole.CUSTOMER), partner.roles());
    assertTrue(
        businessPartnerRepository
            .findById(OperationsTenantId.of(OTHER_TENANT_ID), partner.id())
            .isEmpty());
  }

  @Test
  void shouldRollBackCanonicalStateReceiptAndVersionWhenOutboxAppendFails() {
    jdbcTemplate.execute(
        """
        CREATE FUNCTION reject_operations_outbox_insert()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $function$
        BEGIN
          RAISE EXCEPTION 'forced Operations outbox failure';
        END;
        $function$
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER reject_operations_outbox_insert
        BEFORE INSERT ON operations_outbox_events
        FOR EACH ROW
        EXECUTE FUNCTION reject_operations_outbox_insert()
        """);

    try {
      assertThrows(
          RuntimeException.class,
          () ->
              importBusinessPartnersUseCase.importPage(
                  command(
                      TENANT_ID,
                      SOURCE_SYSTEM_ID,
                      IMPORT_BATCH_ID,
                      PAGE_ACCEPTANCE_ID,
                      List.of(
                          record(
                              "customer-rollback",
                              "v1",
                              FIRST_SOURCE_TIME,
                              "ROLLBACK",
                              "Rollback GmbH")))));
    } finally {
      jdbcTemplate.execute(
          "DROP TRIGGER IF EXISTS reject_operations_outbox_insert ON operations_outbox_events");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_operations_outbox_insert()");
    }

    assertEquals(0, springDataBusinessPartnerRepository.count());
    assertEquals(0, springDataSourceMappingRepository.count());
    assertEquals(0, springDataReceiptRepository.count());
    assertEquals(
        0L,
        jdbcTemplate.queryForObject("SELECT count(*) FROM operations_outbox_events", Long.class));
    assertEquals(
        0L,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM operations_event_stream_versions", Long.class));
  }

  @Test
  void shouldReturnOriginalReceiptWithoutReapplyingARetriedPage() {
    ImportBusinessPartnersCommand original =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", FIRST_SOURCE_TIME, "C-1", "Acme GmbH")));
    BusinessPartnerImportResult first = importBusinessPartnersUseCase.importPage(original);
    BusinessPartnerSourceMappingJpaEntity mappingBefore = mappingEntity("customer-1");
    BusinessPartnerJpaEntity partnerBefore =
        springDataBusinessPartnerRepository
            .findById(mappingBefore.getBusinessPartnerId())
            .orElseThrow();
    clock.setInstant(SECOND_IMPORT_TIME);

    BusinessPartnerImportResult replay = importBusinessPartnersUseCase.importPage(original);

    assertEquals(first, replay);
    assertEquals(1, springDataBusinessPartnerRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    BusinessPartnerSourceMappingJpaEntity mappingAfter = mappingEntity("customer-1");
    BusinessPartnerJpaEntity partnerAfter =
        springDataBusinessPartnerRepository
            .findById(mappingAfter.getBusinessPartnerId())
            .orElseThrow();
    assertEquals(mappingBefore.getVersion(), mappingAfter.getVersion());
    assertEquals(partnerBefore.getVersion(), partnerAfter.getVersion());
    assertEquals(FIRST_IMPORT_TIME, mappingAfter.getUpdatedAt());
    assertEquals(FIRST_IMPORT_TIME, partnerAfter.getUpdatedAt());
    assertEquals(
        1L,
        jdbcTemplate.queryForObject("SELECT count(*) FROM operations_outbox_events", Long.class));
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            "SELECT max(last_version) FROM operations_event_stream_versions", Long.class));
  }

  @Test
  void shouldRejectAcceptanceIdReuseWithDifferentPayloadWithoutMutation() {
    ImportBusinessPartnersCommand original =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", FIRST_SOURCE_TIME, "C-1", "Acme GmbH")));
    importBusinessPartnersUseCase.importPage(original);

    ImportBusinessPartnersCommand conflicting =
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-2", "v1", FIRST_SOURCE_TIME, "C-2", "Conflict")));

    assertThrows(
        ImportPageAcceptanceConflictException.class,
        () -> importBusinessPartnersUseCase.importPage(conflicting));
    assertEquals(1, springDataBusinessPartnerRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
  }

  @Test
  void shouldApplyNewerSourceVersionAndIgnoreOlderVersion() {
    importBusinessPartnersUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("customer-1", "v1", FIRST_SOURCE_TIME, "C-1", "Acme GmbH"))));
    clock.setInstant(SECOND_IMPORT_TIME);
    BusinessPartnerImportResult updated =
        importBusinessPartnersUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(record("customer-1", "v2", SECOND_SOURCE_TIME, "C-1", "Acme AG"))));
    BusinessPartnerImportResult stale =
        importBusinessPartnersUseCase.importPage(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                UUID.randomUUID(),
                List.of(
                    record(
                        "customer-1",
                        "v0",
                        FIRST_SOURCE_TIME.minusSeconds(1),
                        "C-1",
                        "Stale name"))));

    assertEquals(1, updated.updatedCount());
    assertEquals(1, stale.staleCount());
    BusinessPartnerSourceMappingJpaEntity mapping = mappingEntity("customer-1");
    BusinessPartnerJpaEntity partner =
        springDataBusinessPartnerRepository.findById(mapping.getBusinessPartnerId()).orElseThrow();
    assertEquals("v2", mapping.getSourceVersion());
    assertEquals(1L, mapping.getVersion());
    assertEquals("Acme AG", partner.getDisplayName());
    assertEquals(1L, partner.getVersion());
    assertEquals(SECOND_IMPORT_TIME, mapping.getUpdatedAt());
    assertEquals(SECOND_IMPORT_TIME, partner.getUpdatedAt());
  }

  @Test
  void shouldKeepSourceIdentitiesAndReceiptsTenantAndSourceScoped() {
    for (ImportBusinessPartnersCommand command :
        List.of(
            command(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared", "v1", FIRST_SOURCE_TIME, "C-1", "First"))),
            command(
                OTHER_TENANT_ID,
                SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared", "v1", FIRST_SOURCE_TIME, "C-1", "Other tenant"))),
            command(
                TENANT_ID,
                OTHER_SOURCE_SYSTEM_ID,
                IMPORT_BATCH_ID,
                PAGE_ACCEPTANCE_ID,
                List.of(record("shared", "v1", FIRST_SOURCE_TIME, "C-1", "Other source"))))) {
      BusinessPartnerImportResult result = importBusinessPartnersUseCase.importPage(command);
      assertEquals(1, result.createdCount());
    }

    assertEquals(3, springDataBusinessPartnerRepository.count());
    assertEquals(3, springDataSourceMappingRepository.count());
    assertEquals(3, springDataReceiptRepository.count());
  }

  @Test
  void shouldRollBackTheWholePageWhenARecordHasConflictingOrderingEvidence() {
    importBusinessPartnersUseCase.importPage(
        command(
            TENANT_ID,
            SOURCE_SYSTEM_ID,
            IMPORT_BATCH_ID,
            PAGE_ACCEPTANCE_ID,
            List.of(record("existing", "v1", FIRST_SOURCE_TIME, "C-1", "Existing"))));
    UUID failedAcceptanceId = UUID.randomUUID();

    assertThrows(
        ConflictingSourceRecordVersionException.class,
        () ->
            importBusinessPartnersUseCase.importPage(
                command(
                    TENANT_ID,
                    SOURCE_SYSTEM_ID,
                    IMPORT_BATCH_ID,
                    failedAcceptanceId,
                    List.of(
                        record("new", "v1", SECOND_SOURCE_TIME, "C-2", "Must roll back"),
                        record("existing", "v2", FIRST_SOURCE_TIME, "C-1", "Conflict")))));

    assertEquals(1, springDataBusinessPartnerRepository.count());
    assertEquals(1, springDataSourceMappingRepository.count());
    assertEquals(1, springDataReceiptRepository.count());
    assertTrue(
        sourceMappingRepository
            .findBySourceIdentity(
                OperationsTenantId.of(TENANT_ID),
                SourceSystemId.of(SOURCE_SYSTEM_ID),
                SourceRecordIdentity.sourceRecordId("new"))
            .isEmpty());
  }

  @Test
  void shouldPersistAndReplayAnEmptyAcceptedPage() {
    BusinessPartnerImportResult first =
        importBusinessPartnersUseCase.importPage(
            command(TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of()));
    clock.setInstant(SECOND_IMPORT_TIME);
    BusinessPartnerImportResult replay =
        importBusinessPartnersUseCase.importPage(
            command(TENANT_ID, SOURCE_SYSTEM_ID, IMPORT_BATCH_ID, PAGE_ACCEPTANCE_ID, List.of()));

    assertEquals(first, replay);
    assertEquals(0, replay.fetchedCount());
    assertEquals(FIRST_IMPORT_TIME, replay.acceptedAt());
    assertEquals(1, springDataReceiptRepository.count());
  }

  private BusinessPartnerSourceMapping mapping(
      UUID tenantId, UUID sourceSystemId, String sourceRecordId) {
    return sourceMappingRepository
        .findBySourceIdentity(
            OperationsTenantId.of(tenantId),
            SourceSystemId.of(sourceSystemId),
            SourceRecordIdentity.sourceRecordId(sourceRecordId))
        .orElseThrow();
  }

  private BusinessPartnerSourceMappingJpaEntity mappingEntity(String sourceRecordId) {
    return springDataSourceMappingRepository
        .findById(
            new BusinessPartnerSourceMappingJpaId(
                TENANT_ID,
                SOURCE_SYSTEM_ID,
                SourceRecordIdentity.Kind.SOURCE_RECORD_ID,
                sourceRecordId))
        .orElseThrow();
  }

  private static ImportBusinessPartnersCommand command(
      UUID tenantId,
      UUID sourceSystemId,
      UUID importBatchId,
      UUID pageAcceptanceId,
      List<BusinessPartnerImportRecord> records) {
    return new ImportBusinessPartnersCommand(
        tenantId, sourceSystemId, importBatchId, pageAcceptanceId, records);
  }

  private static BusinessPartnerImportRecord record(
      String sourceRecordId,
      String sourceVersion,
      Instant sourceModifiedAt,
      String customerNumber,
      String displayName) {
    return new BusinessPartnerImportRecord(
        SourceRecordIdentity.sourceRecordId(sourceRecordId),
        new SourceRecordVersion(sourceVersion),
        Optional.ofNullable(sourceModifiedAt),
        new BusinessPartnerProfile(customerNumber, displayName, Optional.empty()));
  }

  static final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
    }

    void setInstant(Instant instant) {
      this.instant = Objects.requireNonNull(instant, "Instant cannot be null");
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfiguration {

    @Bean
    @Primary
    MutableClock operationsImportTestClock() {
      return new MutableClock(FIRST_IMPORT_TIME);
    }
  }
}
