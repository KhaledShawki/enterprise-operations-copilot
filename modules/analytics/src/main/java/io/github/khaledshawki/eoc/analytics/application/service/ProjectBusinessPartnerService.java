package io.github.khaledshawki.eoc.analytics.application.service;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionConcurrentModificationException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionVersionConflictException;
import io.github.khaledshawki.eoc.analytics.application.model.ProjectionApplyResult;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerCommand;
import io.github.khaledshawki.eoc.analytics.application.port.in.ProjectBusinessPartnerUseCase;
import io.github.khaledshawki.eoc.analytics.application.port.out.BusinessPartnerProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.BusinessPartnerProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.util.Objects;
import java.util.Optional;

public final class ProjectBusinessPartnerService implements ProjectBusinessPartnerUseCase {

  private static final String AGGREGATE_TYPE = "BUSINESS_PARTNER";

  private final BusinessPartnerProjectionRepository repository;

  public ProjectBusinessPartnerService(BusinessPartnerProjectionRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "Business partner projection repository cannot be null");
  }

  @Override
  public ProjectionApplyResult project(ProjectBusinessPartnerCommand command) {
    Objects.requireNonNull(command, "Business partner projection command cannot be null");
    AnalyticsTenantId tenantId = AnalyticsTenantId.of(command.tenantId());
    ProjectionCursor source =
        new ProjectionCursor(command.eventId(), command.aggregateVersion(), command.occurredAt());
    BusinessPartnerProjection incoming =
        new BusinessPartnerProjection(
            tenantId,
            command.businessPartnerId(),
            command.partnerNumber(),
            command.displayName(),
            command.roles(),
            source);

    Optional<BusinessPartnerProjection> current =
        repository.findById(tenantId, command.businessPartnerId());
    if (current.isEmpty()) {
      ProjectionSequence.requireInitial(source, AGGREGATE_TYPE, command.businessPartnerId());
      persist(incoming, 0);
      return ProjectionApplyResult.applied(source.eventId(), source.aggregateVersion());
    }

    BusinessPartnerProjection existing = current.orElseThrow();
    requireRequestedIdentity(existing, tenantId, command);
    ProjectionSequence.SequenceDecision decision =
        ProjectionSequence.evaluate(
            existing.source(), source, AGGREGATE_TYPE, command.businessPartnerId());
    if (decision == ProjectionSequence.SequenceDecision.SAME_VERSION) {
      if (existing.equals(incoming)) {
        return ProjectionApplyResult.duplicate(source.eventId(), source.aggregateVersion());
      }
      throw new AnalyticsProjectionVersionConflictException(
          AGGREGATE_TYPE,
          command.businessPartnerId(),
          source.aggregateVersion(),
          "the same event identity carries different business partner projection facts");
    }

    persist(incoming, existing.source().aggregateVersion());
    return ProjectionApplyResult.applied(source.eventId(), source.aggregateVersion());
  }

  private static void requireRequestedIdentity(
      BusinessPartnerProjection projection,
      AnalyticsTenantId tenantId,
      ProjectBusinessPartnerCommand command) {
    if (!projection.tenantId().equals(tenantId)
        || !projection.businessPartnerId().equals(command.businessPartnerId())) {
      throw new AnalyticsProjectionStateCorruptedException(
          "business partner repository returned a projection for another tenant or aggregate");
    }
  }

  private void persist(BusinessPartnerProjection projection, long expectedCurrentVersion) {
    if (!repository.saveIfCurrentVersion(projection, expectedCurrentVersion)) {
      throw new AnalyticsProjectionConcurrentModificationException(
          AGGREGATE_TYPE, projection.businessPartnerId(), expectedCurrentVersion);
    }
  }
}
