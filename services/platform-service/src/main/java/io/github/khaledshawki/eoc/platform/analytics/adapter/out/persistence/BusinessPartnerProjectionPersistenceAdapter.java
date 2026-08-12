package io.github.khaledshawki.eoc.platform.analytics.adapter.out.persistence;

import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsEventConsumptionException;
import io.github.khaledshawki.eoc.analytics.application.exception.AnalyticsProjectionStateCorruptedException;
import io.github.khaledshawki.eoc.analytics.application.port.out.BusinessPartnerProjectionRepository;
import io.github.khaledshawki.eoc.analytics.domain.model.AnalyticsTenantId;
import io.github.khaledshawki.eoc.analytics.domain.model.BusinessPartnerProjection;
import io.github.khaledshawki.eoc.analytics.domain.model.ProjectionCursor;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
class BusinessPartnerProjectionPersistenceAdapter implements BusinessPartnerProjectionRepository {

  private static final String PERSISTENCE_UNAVAILABLE =
      "analytics-projection-persistence-unavailable";

  private final JdbcTemplate jdbcTemplate;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  BusinessPartnerProjectionPersistenceAdapter(
      JdbcTemplate jdbcTemplate, JsonMapper jsonMapper, Clock clock) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JDBC template cannot be null");
    this.jsonMapper = Objects.requireNonNull(jsonMapper, "JSON mapper cannot be null");
    this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
  }

  @Override
  public Optional<BusinessPartnerProjection> findById(
      AnalyticsTenantId tenantId, UUID businessPartnerId) {
    Objects.requireNonNull(tenantId, "Analytics tenant id cannot be null");
    Objects.requireNonNull(businessPartnerId, "Business partner id cannot be null");
    try {
      return jdbcTemplate
          .query(
              """
              SELECT tenant_id,
                     business_partner_id,
                     partner_number,
                     display_name,
                     roles::text AS roles_json,
                     source_event_id,
                     aggregate_version,
                     occurred_at
              FROM analytics_business_partner_projections
              WHERE tenant_id = ? AND business_partner_id = ?
              """,
              (resultSet, rowNum) ->
                  map(
                      resultSet.getObject("tenant_id", UUID.class),
                      resultSet.getObject("business_partner_id", UUID.class),
                      resultSet.getString("partner_number"),
                      resultSet.getString("display_name"),
                      resultSet.getString("roles_json"),
                      resultSet.getObject("source_event_id", UUID.class),
                      resultSet.getLong("aggregate_version"),
                      resultSet.getTimestamp("occurred_at").toInstant()),
              tenantId.value(),
              businessPartnerId)
          .stream()
          .findFirst();
    } catch (AnalyticsProjectionStateCorruptedException exception) {
      throw exception;
    } catch (DataAccessException exception) {
      throw retryable(exception);
    }
  }

  @Override
  public boolean saveIfCurrentVersion(
      BusinessPartnerProjection projection, long expectedCurrentVersion) {
    Objects.requireNonNull(projection, "Business partner projection cannot be null");
    if (expectedCurrentVersion < 0) {
      throw new IllegalArgumentException("Expected current projection version cannot be negative");
    }
    AnalyticsPersistenceTransactionGuard.requireActive();
    String roles = encodeRoles(projection.roles());
    Timestamp projectedAt = Timestamp.from(clock.instant());
    try {
      if (expectedCurrentVersion == 0) {
        return jdbcTemplate.update(
                """
                INSERT INTO analytics_business_partner_projections (
                  tenant_id,
                  business_partner_id,
                  partner_number,
                  display_name,
                  roles,
                  source_event_id,
                  aggregate_version,
                  occurred_at,
                  projected_at
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                ON CONFLICT (tenant_id, business_partner_id) DO NOTHING
                """,
                projection.tenantId().value(),
                projection.businessPartnerId(),
                projection.partnerNumber(),
                projection.displayName(),
                roles,
                projection.source().eventId(),
                projection.source().aggregateVersion(),
                Timestamp.from(projection.source().occurredAt()),
                projectedAt)
            == 1;
      }

      return jdbcTemplate.update(
              """
              UPDATE analytics_business_partner_projections
              SET partner_number = ?,
                  display_name = ?,
                  roles = CAST(? AS jsonb),
                  source_event_id = ?,
                  aggregate_version = ?,
                  occurred_at = ?,
                  projected_at = ?
              WHERE tenant_id = ?
                AND business_partner_id = ?
                AND aggregate_version = ?
              """,
              projection.partnerNumber(),
              projection.displayName(),
              roles,
              projection.source().eventId(),
              projection.source().aggregateVersion(),
              Timestamp.from(projection.source().occurredAt()),
              projectedAt,
              projection.tenantId().value(),
              projection.businessPartnerId(),
              expectedCurrentVersion)
          == 1;
    } catch (DataIntegrityViolationException exception) {
      throw corrupted("database constraints rejected a business partner projection", exception);
    } catch (DataAccessException exception) {
      throw retryable(exception);
    }
  }

  private BusinessPartnerProjection map(
      UUID tenantId,
      UUID businessPartnerId,
      String partnerNumber,
      String displayName,
      String rolesJson,
      UUID eventId,
      long aggregateVersion,
      java.time.Instant occurredAt) {
    try {
      String[] roles = jsonMapper.readValue(rolesJson, String[].class);
      TreeSet<String> canonicalRoles = new TreeSet<>(Arrays.asList(roles));
      if (canonicalRoles.size() != roles.length) {
        throw new IllegalArgumentException("Stored business partner roles contain duplicates");
      }
      return new BusinessPartnerProjection(
          AnalyticsTenantId.of(tenantId),
          businessPartnerId,
          partnerNumber,
          displayName,
          Set.copyOf(canonicalRoles),
          new ProjectionCursor(eventId, aggregateVersion, occurredAt));
    } catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
      throw corrupted("stored business partner projection is invalid", exception);
    }
  }

  private String encodeRoles(Set<String> roles) {
    try {
      return jsonMapper.writeValueAsString(roles);
    } catch (JacksonException exception) {
      throw corrupted("business partner roles could not be serialized", exception);
    }
  }

  private static AnalyticsProjectionStateCorruptedException corrupted(
      String detail, Throwable cause) {
    AnalyticsProjectionStateCorruptedException exception =
        new AnalyticsProjectionStateCorruptedException(detail);
    exception.initCause(cause);
    return exception;
  }

  private static AnalyticsEventConsumptionException retryable(DataAccessException cause) {
    return new AnalyticsEventConsumptionException(PERSISTENCE_UNAVAILABLE, true, cause);
  }
}
