package io.github.khaledshawki.eoc.analytics.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionConcurrentModificationException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionGapException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyStatus;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectInvoiceReceivableCommand;
import io.github.khaledshawki.eoc.analytics.application.port.out.InvoiceReceivableProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.InvoiceReceivableProjection;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectInvoiceReceivableServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
  private static final UUID INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

  @Test
  void appliesCanonicalInvoiceSnapshot() {
    FakeRepository repository = new FakeRepository();
    ProjectInvoiceReceivableService service = new ProjectInvoiceReceivableService(repository);

    var result = service.project(command(UUID.randomUUID(), 1, "0.00", "OPEN"));

    assertEquals(ProjectionApplyStatus.APPLIED, result.status());
    assertEquals(
        new BigDecimal("100.00"), repository.stored.orElseThrow().outstandingAmount().amount());
    assertEquals(0, repository.lastExpectedVersion);
  }

  @Test
  void exactReplayIsDuplicateWithoutWrite() {
    FakeRepository repository = new FakeRepository();
    ProjectInvoiceReceivableService service = new ProjectInvoiceReceivableService(repository);
    ProjectInvoiceReceivableCommand command = command(UUID.randomUUID(), 1, "0.00", "OPEN");
    service.project(command);
    repository.writeAttempted = false;

    var result = service.project(command);

    assertEquals(ProjectionApplyStatus.DUPLICATE, result.status());
    assertFalse(repository.writeAttempted);
  }

  @Test
  void nextVersionReplacesSnapshotWithVersionFence() {
    FakeRepository repository = new FakeRepository();
    ProjectInvoiceReceivableService service = new ProjectInvoiceReceivableService(repository);
    service.project(command(UUID.randomUUID(), 1, "0.00", "OPEN"));

    service.project(command(UUID.randomUUID(), 2, "25.00", "PARTIALLY_PAID"));

    assertEquals(1, repository.lastExpectedVersion);
    assertEquals(
        new BigDecimal("75.00"), repository.stored.orElseThrow().outstandingAmount().amount());
  }

  @Test
  void initialGapIsRejected() {
    ProjectInvoiceReceivableService service =
        new ProjectInvoiceReceivableService(new FakeRepository());

    assertThrows(
        AnalyticsProjectionVersionGapException.class,
        () -> service.project(command(UUID.randomUUID(), 2, "25.00", "PARTIALLY_PAID")));
  }

  @Test
  void sameEventAndVersionWithChangedFactsIsRejected() {
    FakeRepository repository = new FakeRepository();
    ProjectInvoiceReceivableService service = new ProjectInvoiceReceivableService(repository);
    UUID eventId = UUID.randomUUID();
    service.project(command(eventId, 1, "0.00", "OPEN"));

    assertThrows(
        AnalyticsProjectionVersionConflictException.class,
        () -> service.project(command(eventId, 1, "25.00", "PARTIALLY_PAID")));
  }

  @Test
  void invalidContractStatusIsRejectedByAnalyticsModel() {
    ProjectInvoiceReceivableService service =
        new ProjectInvoiceReceivableService(new FakeRepository());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.project(command(UUID.randomUUID(), 1, "25.00", "OPEN")));
  }

  @Test
  void concurrentConditionalWriteFailureIsSurfaced() {
    FakeRepository repository = new FakeRepository();
    repository.acceptWrites = false;
    ProjectInvoiceReceivableService service = new ProjectInvoiceReceivableService(repository);

    assertThrows(
        AnalyticsProjectionConcurrentModificationException.class,
        () -> service.project(command(UUID.randomUUID(), 1, "0.00", "OPEN")));
  }

  private static ProjectInvoiceReceivableCommand command(
      UUID eventId, long aggregateVersion, String paidAmount, String status) {
    return new ProjectInvoiceReceivableCommand(
        eventId,
        TENANT_ID,
        INVOICE_ID,
        aggregateVersion,
        Instant.parse("2026-08-12T00:00:00Z").plusSeconds(aggregateVersion - 1),
        CUSTOMER_ID,
        "INV-100",
        new BigDecimal("100.00"),
        new BigDecimal(paidAmount),
        "EUR",
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 31),
        false,
        status);
  }

  private static final class FakeRepository implements InvoiceReceivableProjectionRepository {

    private Optional<InvoiceReceivableProjection> stored = Optional.empty();
    private long lastExpectedVersion = -1;
    private boolean acceptWrites = true;
    private boolean writeAttempted;

    @Override
    public Optional<InvoiceReceivableProjection> findById(
        AnalyticsTenantId tenantId, UUID invoiceId) {
      return stored.filter(
          projection ->
              projection.tenantId().equals(tenantId) && projection.invoiceId().equals(invoiceId));
    }

    @Override
    public boolean saveIfCurrentVersion(
        InvoiceReceivableProjection projection, long expectedCurrentVersion) {
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
