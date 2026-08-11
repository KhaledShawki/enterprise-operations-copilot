package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionConcurrentModificationException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionRegressionException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyStatus;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerCommand;
import io.github.khaledshawki.eoc.analytics.application.port.out.BusinessPartnerProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.BusinessPartnerProjection;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectBusinessPartnerServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-12T00:00:00Z");

  @Test
  void appliesInitialVersionWithAbsentExpectation() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    ProjectBusinessPartnerCommand command = command(UUID.randomUUID(), 1, "Acme AG");

    var result = service.project(command);

    assertEquals(ProjectionApplyStatus.APPLIED, result.status());
    assertEquals(0, repository.lastExpectedVersion);
    assertEquals(1, repository.stored.orElseThrow().source().aggregateVersion());
  }

  @Test
  void exactCurrentVersionReplayIsDuplicateWithoutWrite() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    ProjectBusinessPartnerCommand command = command(UUID.randomUUID(), 1, "Acme AG");
    service.project(command);
    repository.writeAttempted = false;

    var result = service.project(command);

    assertEquals(ProjectionApplyStatus.DUPLICATE, result.status());
    assertFalse(repository.writeAttempted);
  }

  @Test
  void sameVersionWithDifferentEventIdentityFailsClosed() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    service.project(command(UUID.randomUUID(), 1, "Acme AG"));

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () -> service.project(command(UUID.randomUUID(), 1, "Acme AG")));
  }

  @Test
  void sameEventIdentityWithDifferentFactsFailsClosed() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    UUID eventId = UUID.randomUUID();
    service.project(command(eventId, 1, "Acme AG"));

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () -> service.project(command(eventId, 1, "Different AG")));
  }

  @Test
  void initialVersionGapIsRejected() {
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(new FakeRepository());

    AnalyticsProjectionVersionGapException exception =
        assertThrows(
            AnalyticsProjectionVersionGapException.class,
            () -> service.project(command(UUID.randomUUID(), 2, "Acme AG")));

    assertEquals(1, exception.expectedVersion());
    assertEquals(2, exception.actualVersion());
  }

  @Test
  void nextVersionUsesCurrentVersionAsOptimisticFence() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    service.project(command(UUID.randomUUID(), 1, "Acme AG"));

    service.project(command(UUID.randomUUID(), 2, "Acme Schweiz AG"));

    assertEquals(1, repository.lastExpectedVersion);
    assertEquals("Acme Schweiz AG", repository.stored.orElseThrow().displayName());
  }

  @Test
  void skippedVersionIsRejected() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    service.project(command(UUID.randomUUID(), 1, "Acme AG"));

    assertThrows(
        AnalyticsProjectionVersionGapException.class,
        () -> service.project(command(UUID.randomUUID(), 3, "Acme AG")));
  }

  @Test
  void olderVersionIsRejectedRatherThanSilentlyIgnored() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    service.project(command(UUID.randomUUID(), 1, "Acme AG"));
    service.project(command(UUID.randomUUID(), 2, "Acme Schweiz AG"));

    assertThrows(
        AnalyticsProjectionVersionRegressionException.class,
        () -> service.project(command(UUID.randomUUID(), 1, "Acme AG")));
  }

  @Test
  void reusedEventIdAcrossVersionsIsRejected() {
    FakeRepository repository = new FakeRepository();
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);
    UUID eventId = UUID.randomUUID();
    service.project(command(eventId, 1, "Acme AG"));

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () -> service.project(command(eventId, 2, "Acme Schweiz AG")));
  }

  @Test
  void concurrentConditionalWriteFailureIsSurfaced() {
    FakeRepository repository = new FakeRepository();
    repository.acceptWrites = false;
    ProjectBusinessPartnerService service = new ProjectBusinessPartnerService(repository);

    assertThrows(
        AnalyticsProjectionConcurrentModificationException.class,
        () -> service.project(command(UUID.randomUUID(), 1, "Acme AG")));
    assertTrue(repository.writeAttempted);
  }

  private static ProjectBusinessPartnerCommand command(
      UUID eventId, long aggregateVersion, String displayName) {
    return new ProjectBusinessPartnerCommand(
        eventId,
        TENANT_ID,
        BUSINESS_PARTNER_ID,
        aggregateVersion,
        OCCURRED_AT.plusSeconds(aggregateVersion - 1),
        "C-100",
        displayName,
        Set.of("CUSTOMER"));
  }

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID BUSINESS_PARTNER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  private static final class FakeRepository implements BusinessPartnerProjectionRepository {

    private Optional<BusinessPartnerProjection> stored = Optional.empty();
    private long lastExpectedVersion = -1;
    private boolean acceptWrites = true;
    private boolean writeAttempted;

    @Override
    public Optional<BusinessPartnerProjection> findById(
        AnalyticsTenantId tenantId, UUID businessPartnerId) {
      return stored.filter(
          projection ->
              projection.tenantId().equals(tenantId)
                  && projection.businessPartnerId().equals(businessPartnerId));
    }

    @Override
    public boolean saveIfCurrentVersion(
        BusinessPartnerProjection projection, long expectedCurrentVersion) {
      writeAttempted = true;
      lastExpectedVersion = expectedCurrentVersion;
      if (!acceptWrites) {
        return false;
      }
      long actualVersion = stored.map(value -> value.source().aggregateVersion()).orElse(0L);
      if (actualVersion != expectedCurrentVersion) {
        return false;
      }
      stored = Optional.of(projection);
      return true;
    }
  }
}
